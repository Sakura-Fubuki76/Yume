#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <mutex>
#include <dirent.h>
#include <sys/stat.h>

#define LOG_TAG "AssRendererJNI"
#ifdef NDEBUG
#define LOGD(...) ((void)0)
#else
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if __has_include(<ass/ass.h>)
#include <ass/ass.h>
#define HAS_LIBASS 1
#else
#define HAS_LIBASS 0

#define YCBCR_NONE 2
struct ASS_Library {};
struct ASS_Renderer {};
struct ASS_Track {
    int YCbCrMatrix;
};
struct ASS_Image {
    int w, h, stride;
    int dst_x, dst_y;
    uint32_t color;
    uint8_t* bitmap;
    ASS_Image* next;
};
#endif

#if HAS_LIBASS

static inline bool ycbcr_is_bt709(int matrix) {
    return matrix == YCBCR_BT709_TV || matrix == YCBCR_BT709_PC;
}

static inline uint32_t ycbcr_mangle_color(uint32_t c, bool bt709) {
    (void)bt709;
    return c;
}

static void scan_fonts_dir(ASS_Library* lib, const char* path) {
    DIR* dir = opendir(path);
    if (!dir) {
        LOGE("Cannot open fonts directory: %s", path);
        return;
    }

    ass_set_fonts_dir(lib, path);
    LOGD("Registered fonts directory: %s", path);

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;

        size_t pathLen = strlen(path);
        size_t nameLen = strlen(entry->d_name);
        char* fullPath = (char*)malloc(pathLen + nameLen + 2);
        if (!fullPath) continue;
        snprintf(fullPath, pathLen + nameLen + 2, "%s/%s", path, entry->d_name);

        struct stat st;
        if (stat(fullPath, &st) == 0 && S_ISDIR(st.st_mode)) {
            scan_fonts_dir(lib, fullPath);
        }
        free(fullPath);
    }
    closedir(dir);
}
#endif

struct AssContext {
    ASS_Library* lib;
    ASS_Renderer* renderer;
    ASS_Track* track;
    ANativeWindow* window;
    int width;
    int height;
    int storageW;
    int storageH;
    int ycbcrMatrix;
    char* fontconfigPath;
    std::mutex renderMutex;

    AssContext() : lib(nullptr), renderer(nullptr), track(nullptr),
                   window(nullptr), width(0), height(0),
                   storageW(0), storageH(0), ycbcrMatrix(0),
                   fontconfigPath(nullptr) {}
    ~AssContext() {
#if HAS_LIBASS
        if (track) ass_free_track(track);
        if (renderer) ass_renderer_done(renderer);
        if (lib) ass_library_done(lib);
#endif
        if (window) ANativeWindow_release(window);
        free(fontconfigPath);
    }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeInit(
    JNIEnv* env, jclass, jstring configPath)
{
    AssContext* ctx = new AssContext();
#if HAS_LIBASS
    ctx->lib = ass_library_init();
    if (ctx->lib) {
        ass_set_extract_fonts(ctx->lib, 1);

        ass_set_fonts_dir(ctx->lib, "/system/fonts");
        ass_set_fonts_dir(ctx->lib, "/product/fonts");
        ass_set_fonts_dir(ctx->lib, "/vendor/fonts");

        ctx->renderer = ass_renderer_init(ctx->lib);
        if (ctx->renderer) {
            ass_set_shaper(ctx->renderer, ASS_SHAPING_COMPLEX);

            ass_set_hinting(ctx->renderer, ASS_HINTING_LIGHT);

            const char* cfg = nullptr;
            if (configPath) {
                cfg = env->GetStringUTFChars(configPath, nullptr);
                LOGD("Using fontconfig config: %s", cfg);
            }

            ass_set_fonts(ctx->renderer, nullptr, nullptr,
                          ASS_FONTPROVIDER_AUTODETECT, cfg, 1);
            if (cfg) {
                env->ReleaseStringUTFChars(configPath, cfg);
            }
        }
    }
#else
    LOGE("Compiled without libass headers! Native rendering will not work.");
#endif
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetFontsDir(
    JNIEnv* env, jclass, jlong handle, jstring path)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    if (!pathStr) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
#if HAS_LIBASS
    if (ctx->lib) {
        scan_fonts_dir(ctx->lib, pathStr);

        if (ctx->renderer) {
            ass_set_fonts(ctx->renderer, nullptr, "sans-serif",
                          1 , nullptr, 1);
        }
    }
#else
    (void)pathStr;
#endif
    env->ReleaseStringUTFChars(path, pathStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeLoadTrack(
    JNIEnv* env, jclass, jlong handle, jbyteArray data, jint length)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    ctx->ycbcrMatrix = 0;

#if HAS_LIBASS
    if (ctx->track) {
        ass_free_track(ctx->track);
        ctx->track = nullptr;
    }
    if (ctx->lib && data && length > 0) {
        jbyte* buf = env->GetByteArrayElements(data, nullptr);
        ctx->track = ass_read_memory(ctx->lib, reinterpret_cast<char*>(buf),
                                     length, "UTF-8");
        env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

        if (ctx->track) {
            ass_track_set_feature(ctx->track, ASS_FEATURE_WRAP_UNICODE, 1);

            int playResX = ctx->track->PlayResX;
            int playResY = ctx->track->PlayResY;
            if (playResX > 0 && playResY > 0 && ctx->renderer) {
                ass_set_storage_size(ctx->renderer, playResX, playResY);
                ctx->storageW = playResX;
                ctx->storageH = playResY;
                LOGD("Storage size from PlayRes: %dx%d", playResX, playResY);
            }

            ctx->ycbcrMatrix = ctx->track->YCbCrMatrix;
            if (ctx->ycbcrMatrix != YCBCR_NONE) {
                const char* matrixName = ycbcr_is_bt709(ctx->ycbcrMatrix)
                    ? "BT.709" : "BT.601";
                LOGD("YCbCr mangling enabled: matrix=%s (0x%02x)",
                     matrixName, ctx->ycbcrMatrix);
            } else {
                LOGD("YCbCr mangling disabled (YCBCR_NONE)");
            }
        }
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetSurface(
    JNIEnv* env, jclass, jlong handle, jobject surface)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    if (ctx->window) {
        ANativeWindow_release(ctx->window);
        ctx->window = nullptr;
    }

    if (surface) {
        ctx->window = ANativeWindow_fromSurface(env, surface);
        if (ctx->window && ctx->width > 0 && ctx->height > 0) {
            ANativeWindow_setBuffersGeometry(ctx->window, ctx->width, ctx->height, WINDOW_FORMAT_RGBA_8888);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetStorageSize(
    JNIEnv*, jclass, jlong handle, jint width, jint height)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    ctx->storageW = width;
    ctx->storageH = height;
#if HAS_LIBASS
    if (ctx->renderer && width > 0 && height > 0) {
        ass_set_storage_size(ctx->renderer, width, height);
        LOGD("Storage size set: %dx%d", width, height);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetFrameSize(
    JNIEnv*, jclass, jlong handle, jint width, jint height)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    ctx->width = width;
    ctx->height = height;
#if HAS_LIBASS
    if (ctx->renderer) {
        ass_set_frame_size(ctx->renderer, width, height);
    }
#endif
    if (ctx->window && width > 0 && height > 0) {
        ANativeWindow_setBuffersGeometry(ctx->window, width, height, WINDOW_FORMAT_RGBA_8888);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeFlushEvents(
    JNIEnv*, jclass, jlong handle)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
#if HAS_LIBASS
    if (ctx->track) {
        ass_flush_events(ctx->track);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetStyleOverride(
    JNIEnv* env, jclass, jlong handle,
    jstring fontName, jfloat fontSize, jboolean bold,
    jint textColor, jboolean showBackground, jboolean applyEmbeddedStyles)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
#if HAS_LIBASS
    if (!ctx->renderer) return;

    if (!applyEmbeddedStyles) {

        ctx->ycbcrMatrix = YCBCR_NONE;

        ASS_Style style = {};
        style.FontSize = fontSize > 0.0f ? (double)fontSize : 20.0;
        style.Bold = bold ? 1 : 0;

        uint8_t a = (textColor >> 24) & 0xFF;
        uint8_t r = (textColor >> 16) & 0xFF;
        uint8_t g = (textColor >>  8) & 0xFF;
        uint8_t b = textColor & 0xFF;

        style.PrimaryColour   = (r << 24) | (g << 16) | (b << 8) | (255 - a);
        style.SecondaryColour = (r << 24) | (g << 16) | (b << 8) | (255 - a);
        style.OutlineColour   = 0x000000FF;
        style.BackColour      = showBackground ? 0x000000FF : 0x00000000;
        style.Outline         = 2.0;
        style.Shadow          = 1.0;
        style.BorderStyle     = 1;
        style.ScaleX          = 1.0;
        style.ScaleY          = 1.0;

        const char* fname = nullptr;
        if (fontName) {
            fname = env->GetStringUTFChars(fontName, nullptr);
        }
        if (fname) {
            style.FontName = strdup(fname);
            env->ReleaseStringUTFChars(fontName, fname);
        }

        ass_set_selective_style_override(ctx->renderer, &style);

        int bits = ASS_OVERRIDE_BIT_FONT_NAME
                 | ASS_OVERRIDE_BIT_FONT_SIZE_FIELDS
                 | ASS_OVERRIDE_BIT_COLORS
                 | ASS_OVERRIDE_BIT_ATTRIBUTES
                 | ASS_OVERRIDE_BIT_BORDER;
        ass_set_selective_style_override_enabled(ctx->renderer, bits);

        LOGD("Style override applied: font=%s, size=%.1f, bold=%d, color=#%02X%02X%02X%02X",
             fname ? fname : "(null)", fontSize, bold, a, r, g, b);

        free(style.FontName);
    } else {

        ass_set_selective_style_override_enabled(ctx->renderer, 0);
        LOGD("Style override disabled — using embedded ASS styles (ycbcrMatrix=0x%02x)",
             ctx->ycbcrMatrix);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeRenderFrame(
    JNIEnv* env, jclass, jlong handle, jlong timeMs)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx || !ctx->window) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);

#if HAS_LIBASS
    if (!ctx->renderer || !ctx->track) return;
    int changed = 0;
    ASS_Image* img = ass_render_frame(ctx->renderer, ctx->track, timeMs, &changed);
#else
    ASS_Image* img = nullptr;
#endif

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(ctx->window, &buffer, nullptr) < 0) {
        return;
    }

    uint8_t* pixels = static_cast<uint8_t*>(buffer.bits);
    for (int y = 0; y < buffer.height; ++y) {
        memset(pixels + y * buffer.stride * 4, 0, buffer.width * 4);
    }

#if HAS_LIBASS
    if (img) {
        for (ASS_Image* i = img; i != nullptr; i = i->next) {
            if (i->w == 0 || i->h == 0) continue;

            uint32_t imgColor = i->color;

            const uint8_t r = (imgColor >> 24) & 0xFF;
            const uint8_t g = (imgColor >> 16) & 0xFF;
            const uint8_t b = (imgColor >>  8) & 0xFF;
            const uint8_t a = 255 - (imgColor & 0xFF);

            for (int y = 0; y < i->h; ++y) {
                if (i->dst_y + y < 0 || i->dst_y + y >= buffer.height) continue;

                const uint8_t* src = i->bitmap + y * i->stride;
                auto* dst = reinterpret_cast<uint32_t*>(
                    pixels + (i->dst_y + y) * buffer.stride * 4 + (i->dst_x * 4)
                );

                for (int x = 0; x < i->w; ++x) {
                    if (i->dst_x + x < 0 || i->dst_x + x >= buffer.width) continue;

                    const uint8_t mask = src[x];
                    if (mask == 0) continue;

                    const uint8_t fa = (uint16_t)a * mask / 255;
                    const uint8_t ia = 255 - fa;
                    const uint32_t bg = dst[x];

                    const uint8_t bg_a = (bg >> 24) & 0xFF;
                    const uint8_t bb = (bg >> 16) & 0xFF;
                    const uint8_t bg_g = (bg >>  8) & 0xFF;
                    const uint8_t br = (bg      ) & 0xFF;

                    const uint8_t out_a = fa + (bg_a * ia) / 255;
                    dst[x] = (out_a << 24)
                            | (((b * fa + bb * ia) / 255) << 16)
                            | (((g * fa + bg_g * ia) / 255) << 8)
                            | (((r * fa + br * ia) / 255));
                }
            }
        }
    }
#endif

    ANativeWindow_unlockAndPost(ctx->window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeAddFont(
    JNIEnv* env, jclass, jlong handle, jstring name, jbyteArray data)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    jbyte* fontData = env->GetByteArrayElements(data, nullptr);
    jsize dataSize = env->GetArrayLength(data);

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
#if HAS_LIBASS
    if (ctx->lib && nameStr && fontData && dataSize > 0) {
        ass_add_font(ctx->lib, nameStr, reinterpret_cast<char*>(fontData), dataSize);
        LOGD("Added font: %s (%d bytes)", nameStr, dataSize);
    }
#endif

    if (fontData) env->ReleaseByteArrayElements(data, fontData, JNI_ABORT);
    if (nameStr) env->ReleaseStringUTFChars(name, nameStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeRebuildFontCache(
    JNIEnv*, jclass, jlong handle)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
#if HAS_LIBASS
    if (ctx->renderer) {
        ass_set_fonts(ctx->renderer, nullptr, nullptr,
                      ASS_FONTPROVIDER_AUTODETECT,
                      ctx->fontconfigPath, 1);
        LOGD("Font cache rebuilt (config=%s)",
             ctx->fontconfigPath ? ctx->fontconfigPath : "default");
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeSetFontConfig(
    JNIEnv* env, jclass, jlong handle, jstring configPath)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    if (!ctx) return;

    const char* path = nullptr;
    if (configPath) {
        path = env->GetStringUTFChars(configPath, nullptr);
    }

    std::lock_guard<std::mutex> lock(ctx->renderMutex);

    if (ctx->fontconfigPath) {
        free(ctx->fontconfigPath);
        ctx->fontconfigPath = nullptr;
    }
    if (path) {
        ctx->fontconfigPath = strdup(path);
    }

#if HAS_LIBASS
    if (ctx->renderer) {

        ass_set_fonts(ctx->renderer, nullptr, nullptr,
                      ASS_FONTPROVIDER_AUTODETECT, path, 1);
        LOGD("Fontconfig reconfigured: %s", path ? path : "(default)");
    }
#endif

    if (path) env->ReleaseStringUTFChars(configPath, path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sakurafubuki_yume_feature_player_ass_AssRenderer_nativeRelease(
    JNIEnv*, jclass, jlong handle)
{
    AssContext* ctx = reinterpret_cast<AssContext*>(handle);
    delete ctx;
}
