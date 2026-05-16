package com.sakurafubuki.yume.feature.player.ass

import android.view.Surface

object AssRenderer {
    init {
        System.loadLibrary("ass_jni")
    }

    external fun nativeInit(configPath: String?): Long
    external fun nativeSetFontsDir(handle: Long, path: String)
    external fun nativeAddFont(handle: Long, name: String, data: ByteArray)
    external fun nativeRebuildFontCache(handle: Long)
    external fun nativeSetFontConfig(handle: Long, configPath: String?)
    external fun nativeLoadTrack(handle: Long, data: ByteArray, length: Int)
    external fun nativeSetSurface(handle: Long, surface: Surface?)
    external fun nativeSetStorageSize(handle: Long, width: Int, height: Int)
    external fun nativeSetFrameSize(handle: Long, width: Int, height: Int)
    external fun nativeFlushEvents(handle: Long)
    external fun nativeSetStyleOverride(
        handle: Long,
        fontSize: Float,
        textColor: Int,
        showBackground: Boolean,
        applyEmbeddedStyles: Boolean,
    )
    external fun nativeRenderFrame(handle: Long, timeMs: Long)
    external fun nativeRelease(handle: Long)
}
