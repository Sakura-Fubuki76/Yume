package com.sakurafubuki.yume.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.sakurafubuki.yume.core.common.Logger
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object SpriteSheetGenerator {

    private const val TAG = "SpriteSheetGenerator"

    const val THUMB_WIDTH = 160

    const val THUMB_HEIGHT = 90

    const val COLS = 10

    const val ROWS = 10

    const val FRAME_COUNT = COLS * ROWS

    private const val REMOTE_MAX_FRAMES = 100

    private const val WEBP_QUALITY = 80

    private const val DECODE_TIMEOUT_MS = 3000L

    private const val MAX_CONCURRENT_DOWNLOADS = 8

    private val decoderNameCache = ConcurrentHashMap<String, String>()

    suspend fun generate(
        source: String,
        httpHeaders: Map<String, String>? = null,
        durationMs: Long,
        cacheDir: File,
        cacheKey: String,
        context: Context? = null,
    ): SpriteSheetResult? = withContext(Dispatchers.IO) {
        try {
            cacheDir.mkdirs()
            val spriteFile = File(cacheDir, "$cacheKey.webp")
            val metaFile = File(cacheDir, "$cacheKey.json")

            if (spriteFile.exists() && spriteFile.length() > 0 && metaFile.exists()) {
                val meta = readMetadata(metaFile)
                if (meta != null && meta.durationMs == durationMs) {
                    Logger.d(TAG, "Cache hit for $cacheKey")
                    return@withContext SpriteSheetResult(spriteFile, meta)
                }
            }

            Logger.d(TAG, "Generating sprite sheet for $source, duration=${durationMs}ms")

            val isRemote = source.startsWith("http://", ignoreCase = true) ||
                source.startsWith("https://", ignoreCase = true)

            val isContentUri = source.startsWith("content://", ignoreCase = true)

            val sourcePath = source.substringBefore('?')
            val isMp4 = sourcePath.endsWith(".mp4", ignoreCase = true) ||
                sourcePath.endsWith(".mov", ignoreCase = true)
            val isMkv = sourcePath.endsWith(".mkv", ignoreCase = true) ||
                sourcePath.endsWith(".webm", ignoreCase = true)

            if (isMp4 && (isRemote || !isContentUri)) {
                generateFromMoov(source, httpHeaders, durationMs, cacheDir, cacheKey)
            } else if (isRemote && isMkv) {
                generateFromMkv(source, httpHeaders, durationMs, cacheDir, cacheKey, context)
            } else {
                generateFromMediaExtractor(source, durationMs, spriteFile, metaFile, cacheDir, cacheKey, context)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Sprite sheet generation failed", e)
            null
        }
    }

    private suspend fun generateFromMoov(
        source: String,
        httpHeaders: Map<String, String>?,
        durationMs: Long,
        cacheDir: File,
        cacheKey: String,
    ): SpriteSheetResult? {
        val isLocal = !source.startsWith("http://", ignoreCase = true) &&
            !source.startsWith("https://", ignoreCase = true)

        val spriteFile = File(cacheDir, "$cacheKey.webp")
        val metaFile = File(cacheDir, "$cacheKey.json")

        Logger.d("BUG4_SpriteSheet", "generateFromMoov: isLocal=$isLocal source=${source.take(100)} httpHeaders=$httpHeaders")
        val okHttpClient = if (isLocal) OkHttpClient() else buildOkHttpClient(httpHeaders)
        val mp4Extractor = Mp4KeyframeExtractor(okHttpClient)

        val parsed = if (isLocal) {
            mp4Extractor.loadParsedMoovFromFile(source)
        } else {
            mp4Extractor.loadParsedMoov(source)
        }
        if (parsed == null) {
            Logger.w("BUG4_SpriteSheet", "parsed is null for ${source.take(100)}, falling back to MediaExtractor")
            Logger.w(TAG, "Failed to parse moov for $source, falling back to MediaExtractor")
            return generateFromMediaExtractor(source, durationMs, spriteFile, metaFile, cacheDir, cacheKey)
        }

        val moovInfo = parsed.moovInfo
        Logger.d("BUG4_SpriteSheet", "parsed OK: moovInfo=${moovInfo != null} keyframes=${moovInfo?.keyframes?.size ?: 0} durationMs=${parsed.durationMs}")
        if (moovInfo == null || moovInfo.keyframes.isEmpty()) {
            Logger.w("BUG4_SpriteSheet", "No keyframes in moov for ${source.take(100)}, falling back to MediaExtractor")
            Logger.w(TAG, "No keyframe index in moov for $source, falling back to MediaExtractor")
            return generateFromMediaExtractor(source, durationMs, spriteFile, metaFile, cacheDir, cacheKey)
        }

        val allKeyframes = moovInfo.keyframes
        var displayW = moovInfo.width
        var displayH = moovInfo.height
        val needsRotation = moovInfo.rotation == 90 || moovInfo.rotation == 270
        if (needsRotation) {
            displayW = moovInfo.height
            displayH = moovInfo.width
        }
        val (thumbW, thumbH) = computeThumbDimensions(displayW, displayH)

        val (scaleW, scaleH) = computeThumbDimensions(moovInfo.width, moovInfo.height)
        Logger.d(
            TAG,
            "Moov parsed: ${allKeyframes.size} keyframes, codec=${moovInfo.codecType}, " +
                "video=${moovInfo.width}x${moovInfo.height} rotation=${moovInfo.rotation}° thumb=${thumbW}x$thumbH " +
                "scale=${scaleW}x$scaleH, local=$isLocal",
        )

        val startTime = System.currentTimeMillis()

        val maxFrames = if (isLocal) FRAME_COUNT else REMOTE_MAX_FRAMES
        val actualFrameCount = if (durationMs < 10000) {
            ((durationMs / 100).toInt()).coerceIn(10, maxFrames)
        } else {
            maxFrames
        }

        val gridCols = findBestGrid(actualFrameCount).first
        val gridRows = findBestGrid(actualFrameCount).second
        val intervalMs = durationMs.toDouble() / actualFrameCount
        val metadata = SpriteSheetMetadata(
            cols = gridCols,
            rows = gridRows,
            frameCount = actualFrameCount,
            thumbWidth = thumbW,
            thumbHeight = thumbH,
            intervalMs = intervalMs,
            durationMs = durationMs,
        )

        val spriteSheet = Bitmap.createBitmap(
            gridCols * thumbW,
            gridRows * thumbH,
            Bitmap.Config.ARGB_8888,
        )

        var framesDownloaded = 0
        var bytesDownloaded = 0L

        data class FrameTarget(val gridIndex: Int, val kf: Mp4KeyframeExtractor.KeyframeEntry)

        val targets = (0 until actualFrameCount).mapNotNull { i ->
            val targetTimeMs = (i * intervalMs).toLong()
            val kf = allKeyframes.minByOrNull { kotlin.math.abs(it.timeMs - targetTimeMs) }
            if (kf != null) FrameTarget(i, kf) else null
        }
        Logger.d(TAG, "Selected ${targets.size} targets out of ${allKeyframes.size} keyframes")
        Logger.d("BUG4_SpriteSheet", "Selected ${targets.size} targets, reading keyframe data...")

        val readStart = System.currentTimeMillis()
        val pairedData: List<Pair<FrameTarget, ByteArray?>> = targets.chunked(MAX_CONCURRENT_DOWNLOADS).flatMap { batch ->
            coroutineScope {
                batch.map { target ->
                    async {
                        val data = if (isLocal) {
                            mp4Extractor.readFileRange(source, target.kf.byteOffset, target.kf.byteSize)
                        } else {
                            mp4Extractor.httpRange(source, target.kf.byteOffset, target.kf.byteSize)
                        }

                        if (data != null) bytesDownloaded += data.size
                        target to data
                    }
                }.awaitAll()
            }
        }
        Logger.d(TAG, "Read ${bytesDownloaded / 1024}KB in ${System.currentTimeMillis() - readStart}ms")

        val successEntries = pairedData.mapNotNull { (target, data) ->
            data?.let { target to it }
        }

        val decodeStart = System.currentTimeMillis()
        if (successEntries.isNotEmpty()) {
            val successDataOnly = successEntries.map { it.second }
            mp4Extractor.decodeKeyframesRawImage(moovInfo, successDataOnly) { image, outputFormat, codecName, frameIndex ->
                val target = successEntries.getOrNull(frameIndex)?.first ?: return@decodeKeyframesRawImage

                val forceNV21 = codecName.startsWith("OMX.qcom.", ignoreCase = true)
                val midW = scaleW * 2
                val midH = scaleH * 2
                val scaled = YuvToBitmapBridge.scaleTwoPassFromImage(
                    image,
                    midWidth = midW,
                    midHeight = midH,
                    dstWidth = scaleW,
                    dstHeight = scaleH,
                    forceNV21 = forceNV21,
                ) ?: return@decodeKeyframesRawImage

                val colorStandard = outputFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD, 1)
                val colorRange = outputFormat.getInteger(MediaFormat.KEY_COLOR_RANGE, 2)

                var bitmap = YuvToBitmapBridge.imageToBitmap(
                    yBuf = scaled.y, yRowStride = scaled.strideY, yPixelStride = 1,
                    uBuf = scaled.u, uRowStride = scaled.strideU, uPixelStride = 1,
                    vBuf = scaled.v, vRowStride = scaled.strideV, vPixelStride = 1,
                    cropLeft = 0, cropTop = 0,
                    cropWidth = scaled.width, cropHeight = scaled.height,
                    colorStandard = colorStandard, colorRange = colorRange,
                    forceNV21 = false,
                ) ?: return@decodeKeyframesRawImage

                if (needsRotation) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(moovInfo.rotation.toFloat())
                    val rotated = android.graphics.Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        matrix,
                        true,
                    )
                    bitmap.recycle()
                    bitmap = rotated
                }

                val col = target.gridIndex % gridCols
                val row = target.gridIndex / gridCols
                if (YuvToBitmapBridge.compositeToSheet(bitmap, spriteSheet, col, row, thumbW, thumbH, gridCols)) {
                    framesDownloaded++
                }
                bitmap.recycle()
            }
        }
        Logger.d(TAG, "Decoded+scaled+composited $framesDownloaded/${successEntries.size} frames in ${System.currentTimeMillis() - decodeStart}ms")
        Logger.d("BUG4_SpriteSheet", "Decoded $framesDownloaded/${successEntries.size} frames in ${System.currentTimeMillis() - decodeStart}ms")

        if (framesDownloaded == 0) {
            spriteSheet.recycle()
            Logger.w("BUG4_SpriteSheet", "No frames downloaded for ${source.take(100)}")
            Logger.w(TAG, "No frames downloaded for $source")
            return null
        }

        val saveStart = System.currentTimeMillis()
        FileOutputStream(spriteFile).use { out ->
            spriteSheet.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
        }
        spriteSheet.recycle()

        metadata.saveTo(metaFile)

        val totalElapsed = System.currentTimeMillis() - startTime
        Logger.d(
            TAG,
            "Saved ${spriteFile.length() / 1024}KB in ${System.currentTimeMillis() - saveStart}ms. " +
                "Total: ${totalElapsed}ms, $framesDownloaded frames, ${bytesDownloaded / 1024}KB downloaded",
        )
        Logger.d("BUG4_SpriteSheet", "SUCCESS: ${spriteFile.length() / 1024}KB sprite sheet in ${totalElapsed}ms")
        return SpriteSheetResult(spriteFile, metadata)
    }

    private suspend fun generateFromMkv(
        source: String,
        httpHeaders: Map<String, String>?,
        durationMs: Long,
        cacheDir: File,
        cacheKey: String,
        context: Context? = null,
    ): SpriteSheetResult? {
        val spriteFile = File(cacheDir, "$cacheKey.webp")
        val metaFile = File(cacheDir, "$cacheKey.json")

        Logger.d("BUG4_SpriteSheet", "generateFromMkv: source=${source.take(100)}")
        val okHttpClient = buildOkHttpClient(httpHeaders)
        val mkvExtractor = MkvKeyframeExtractor(okHttpClient)
        val mp4Extractor = Mp4KeyframeExtractor(okHttpClient)

        val parsed = mkvExtractor.loadParsedMkv(source)
        if (parsed == null) {
            Logger.w("BUG4_SpriteSheet", "MKV parsed is null for ${source.take(100)}, falling back to MediaExtractor")
            Logger.w(TAG, "Failed to parse MKV for $source, falling back to MediaExtractor")
            return generateFromMediaExtractor(source, durationMs, spriteFile, metaFile, cacheDir, cacheKey, context)
        }

        val moovInfo = parsed.moovInfo
        Logger.d("BUG4_SpriteSheet", "MKV parsed OK: moovInfo=${moovInfo != null} keyframes=${moovInfo?.keyframes?.size ?: 0}")
        if (moovInfo == null || moovInfo.keyframes.isEmpty()) {
            Logger.w("BUG4_SpriteSheet", "No keyframes in MKV for ${source.take(100)}, falling back to MediaExtractor")
            Logger.w(TAG, "No keyframe index in MKV for $source, falling back to MediaExtractor")
            return generateFromMediaExtractor(source, durationMs, spriteFile, metaFile, cacheDir, cacheKey, context)
        }

        val allKeyframes = moovInfo.keyframes
        var displayW = moovInfo.width
        var displayH = moovInfo.height
        val needsRotation = moovInfo.rotation == 90 || moovInfo.rotation == 270
        if (needsRotation) {
            displayW = moovInfo.height
            displayH = moovInfo.width
        }
        val (thumbW, thumbH) = computeThumbDimensions(displayW, displayH)
        val (scaleW, scaleH) = computeThumbDimensions(moovInfo.width, moovInfo.height)
        Logger.d(
            TAG,
            "MKV parsed: ${allKeyframes.size} keyframes, codec=${moovInfo.codecType}, " +
                "video=${moovInfo.width}x${moovInfo.height} thumb=${thumbW}x$thumbH scale=${scaleW}x$scaleH",
        )

        val startTime = System.currentTimeMillis()

        val maxFrames = REMOTE_MAX_FRAMES
        val actualFrameCount = if (durationMs < 10000) {
            ((durationMs / 100).toInt()).coerceIn(10, maxFrames)
        } else {
            maxFrames
        }

        val gridCols = findBestGrid(actualFrameCount).first
        val gridRows = findBestGrid(actualFrameCount).second
        val intervalMs = durationMs.toDouble() / actualFrameCount
        val metadata = SpriteSheetMetadata(
            cols = gridCols,
            rows = gridRows,
            frameCount = actualFrameCount,
            thumbWidth = thumbW,
            thumbHeight = thumbH,
            intervalMs = intervalMs,
            durationMs = durationMs,
        )

        val spriteSheet = Bitmap.createBitmap(
            gridCols * thumbW,
            gridRows * thumbH,
            Bitmap.Config.ARGB_8888,
        )

        var framesDownloaded = 0
        var bytesDownloaded = 0L

        data class FrameTarget(val gridIndex: Int, val kf: Mp4KeyframeExtractor.KeyframeEntry)

        val targets = (0 until actualFrameCount).mapNotNull { i ->
            val targetTimeMs = (i * intervalMs).toLong()
            val kf = allKeyframes.minByOrNull { kotlin.math.abs(it.timeMs - targetTimeMs) }
            if (kf != null) FrameTarget(i, kf) else null
        }
        Logger.d(TAG, "MKV selected ${targets.size} targets out of ${allKeyframes.size} keyframes")
        Logger.d("BUG4_SpriteSheet", "MKV selected ${targets.size} targets")

        val trackNumber = mkvExtractor.videoTrackNumber
        val readStart = System.currentTimeMillis()
        val pairedData: List<Pair<FrameTarget, ByteArray?>> = targets.chunked(MAX_CONCURRENT_DOWNLOADS).flatMap { batch ->
            coroutineScope {
                batch.map { target ->
                    async {
                        val data = mkvExtractor.downloadMkvKeyframe(
                            source,
                            target.kf.byteOffset,
                            target.kf.byteSize,
                            trackNumber,
                        )
                        if (data != null) bytesDownloaded += data.size
                        target to data
                    }
                }.awaitAll()
            }
        }
        Logger.d(TAG, "MKV read ${bytesDownloaded / 1024}KB in ${System.currentTimeMillis() - readStart}ms")

        val successEntries = pairedData.mapNotNull { (target, data) ->
            data?.let { target to it }
        }

        val decodeStart = System.currentTimeMillis()
        if (successEntries.isNotEmpty()) {
            val successDataOnly = successEntries.map { it.second }
            mp4Extractor.decodeKeyframesRawImage(moovInfo, successDataOnly) { image, outputFormat, codecName, frameIndex ->
                val target = successEntries.getOrNull(frameIndex)?.first ?: return@decodeKeyframesRawImage

                val forceNV21 = codecName.startsWith("OMX.qcom.", ignoreCase = true)
                val midW = scaleW * 2
                val midH = scaleH * 2
                val scaled = YuvToBitmapBridge.scaleTwoPassFromImage(
                    image,
                    midWidth = midW,
                    midHeight = midH,
                    dstWidth = scaleW,
                    dstHeight = scaleH,
                    forceNV21 = forceNV21,
                ) ?: return@decodeKeyframesRawImage

                val colorStandard = outputFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD, 1)
                val colorRange = outputFormat.getInteger(MediaFormat.KEY_COLOR_RANGE, 2)

                var bitmap = YuvToBitmapBridge.imageToBitmap(
                    yBuf = scaled.y, yRowStride = scaled.strideY, yPixelStride = 1,
                    uBuf = scaled.u, uRowStride = scaled.strideU, uPixelStride = 1,
                    vBuf = scaled.v, vRowStride = scaled.strideV, vPixelStride = 1,
                    cropLeft = 0, cropTop = 0,
                    cropWidth = scaled.width, cropHeight = scaled.height,
                    colorStandard = colorStandard, colorRange = colorRange,
                    forceNV21 = false,
                ) ?: return@decodeKeyframesRawImage

                if (needsRotation) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(moovInfo.rotation.toFloat())
                    val rotated = android.graphics.Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        matrix,
                        true,
                    )
                    bitmap.recycle()
                    bitmap = rotated
                }

                val col = target.gridIndex % gridCols
                val row = target.gridIndex / gridCols
                if (YuvToBitmapBridge.compositeToSheet(bitmap, spriteSheet, col, row, thumbW, thumbH, gridCols)) {
                    framesDownloaded++
                }
                bitmap.recycle()
            }
        }
        Logger.d(TAG, "MKV decoded+scaled+composited $framesDownloaded/${successEntries.size} frames in ${System.currentTimeMillis() - decodeStart}ms")
        Logger.d("BUG4_SpriteSheet", "MKV decoded $framesDownloaded/${successEntries.size} frames in ${System.currentTimeMillis() - decodeStart}ms")

        if (framesDownloaded == 0) {
            spriteSheet.recycle()
            Logger.w("BUG4_SpriteSheet", "MKV no frames downloaded for ${source.take(100)}")
            Logger.w(TAG, "MKV no frames for $source")
            return null
        }

        val saveStart = System.currentTimeMillis()
        FileOutputStream(spriteFile).use { out ->
            spriteSheet.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
        }
        spriteSheet.recycle()

        metadata.saveTo(metaFile)

        val totalElapsed = System.currentTimeMillis() - startTime
        Logger.d(
            TAG,
            "MKV saved ${spriteFile.length() / 1024}KB in ${System.currentTimeMillis() - saveStart}ms. " +
                "Total: ${totalElapsed}ms, $framesDownloaded frames, ${bytesDownloaded / 1024}KB downloaded",
        )
        Logger.d("BUG4_SpriteSheet", "MKV SUCCESS: ${spriteFile.length() / 1024}KB sprite sheet in ${totalElapsed}ms")
        return SpriteSheetResult(spriteFile, metadata)
    }

    private fun buildOkHttpClient(httpHeaders: Map<String, String>?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (httpHeaders != null) {
                addInterceptor { chain ->
                    var request = chain.request()
                    for ((key, value) in httpHeaders) {
                        request = request.newBuilder().header(key, value).build()
                    }
                    chain.proceed(request)
                }
            }
        }
        .build()

    private fun generateFromMediaExtractor(
        source: String,
        durationMs: Long,
        spriteFile: File,
        metaFile: File,
        cacheDir: File,
        cacheKey: String,
        context: Context? = null,
    ): SpriteSheetResult? {
        val actualFrameCount = if (durationMs < 10000) {
            ((durationMs / 100).toInt()).coerceIn(10, FRAME_COUNT)
        } else {
            FRAME_COUNT
        }

        val gridCols = findBestGrid(actualFrameCount).first
        val gridRows = findBestGrid(actualFrameCount).second
        val intervalMs = durationMs.toDouble() / actualFrameCount
        val extractor = MediaExtractor()
        if (source.startsWith("content://") && context != null) {
            extractor.setDataSource(context, Uri.parse(source), null)
            Logger.d(TAG, "MediaExtractor setDataSource with content:// URI: $source")
        } else {
            extractor.setDataSource(source)
        }

        val videoTrackIndex = findVideoTrack(extractor)
        if (videoTrackIndex < 0) {
            extractor.release()
            Logger.w(TAG, "No video track found in $source")
            return null
        }
        extractor.selectTrack(videoTrackIndex)
        val trackFormat = extractor.getTrackFormat(videoTrackIndex)

        val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            ?: run {
                extractor.release()
                return null
            }
        val codecName = findBestDecoderForMime(mime)
            ?: run {
                extractor.release()
                Logger.w(TAG, "No decoder for $mime")
                return null
            }

        val encodedW = trackFormat.getInteger(MediaFormat.KEY_WIDTH, 0)
        val encodedH = trackFormat.getInteger(MediaFormat.KEY_HEIGHT, 0)
        val rotation = try {
            trackFormat.getInteger("rotation-degrees")
        } catch (_: Exception) {
            0
        }
        val needsRot = rotation == 90 || rotation == 270
        val displayW = if (needsRot) encodedH else encodedW
        val displayH = if (needsRot) encodedW else encodedH
        val (thumbW, thumbH) = computeThumbDimensions(displayW, displayH)
        val (scaleW, scaleH) = computeThumbDimensions(encodedW, encodedH)

        val metadata = SpriteSheetMetadata(
            cols = gridCols,
            rows = gridRows,
            frameCount = actualFrameCount,
            thumbWidth = thumbW,
            thumbHeight = thumbH,
            intervalMs = intervalMs,
            durationMs = durationMs,
        )

        val spriteSheet = Bitmap.createBitmap(
            gridCols * thumbW,
            gridRows * thumbH,
            Bitmap.Config.ARGB_8888,
        )

        val codec = MediaCodec.createByCodecName(codecName)
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        try {
            val processedPts = HashSet<Long>()

            for (i in 0 until actualFrameCount) {
                val targetTimeUs = (i * intervalMs * 1000).toLong()
                extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val sampleTime = extractor.sampleTime
                if (sampleTime in processedPts && sampleTime >= 0) {
                    extractor.advance()
                    continue
                }
                if (sampleTime >= 0) processedPts.add(sampleTime)

                val col = i % gridCols
                val row = i / gridCols

                val isQcom = codecName.startsWith("OMX.qcom.", ignoreCase = true)
                val midW = scaleW * 2
                val midH = scaleH * 2
                decodeOneFrameToImage(codec, extractor) { image, outputFormat ->
                    val scaled = YuvToBitmapBridge.scaleTwoPassFromImage(
                        image,
                        midWidth = midW,
                        midHeight = midH,
                        dstWidth = scaleW,
                        dstHeight = scaleH,
                        forceNV21 = isQcom,
                    ) ?: return@decodeOneFrameToImage

                    val colorStandard = outputFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD, 1)
                    val colorRange = outputFormat.getInteger(MediaFormat.KEY_COLOR_RANGE, 2)

                    var bitmap = YuvToBitmapBridge.imageToBitmap(
                        yBuf = scaled.y, yRowStride = scaled.strideY, yPixelStride = 1,
                        uBuf = scaled.u, uRowStride = scaled.strideU, uPixelStride = 1,
                        vBuf = scaled.v, vRowStride = scaled.strideV, vPixelStride = 1,
                        cropLeft = 0, cropTop = 0,
                        cropWidth = scaled.width, cropHeight = scaled.height,
                        colorStandard = colorStandard, colorRange = colorRange,
                        forceNV21 = false,
                    ) ?: return@decodeOneFrameToImage

                    if (needsRot) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(rotation.toFloat())
                        val rotated = android.graphics.Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true,
                        )
                        bitmap.recycle()
                        bitmap = rotated
                    }

                    YuvToBitmapBridge.compositeToSheet(bitmap, spriteSheet, col, row, thumbW, thumbH, gridCols)
                    bitmap.recycle()
                }
            }

            FileOutputStream(spriteFile).use { out ->
                spriteSheet.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            }
            spriteSheet.recycle()

            metadata.saveTo(metaFile)

            Logger.d(TAG, "Sprite sheet saved: ${spriteFile.length()} bytes, $actualFrameCount frames")
            return SpriteSheetResult(spriteFile, metadata)
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }

    private fun computeThumbDimensions(videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0) return THUMB_WIDTH to THUMB_HEIGHT
        val maxDim = 160
        return if (videoWidth >= videoHeight) {
            maxDim to maxOf(1, (maxDim * videoHeight) / videoWidth)
        } else {
            maxOf(1, (maxDim * videoWidth) / videoHeight) to maxDim
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                return i
            }
        }
        return -1
    }

    private fun decodeOneFrameToImage(
        codec: MediaCodec,
        extractor: MediaExtractor,
        onImage: (image: Image, outputFormat: MediaFormat) -> Unit,
    ) {
        try {
            codec.flush()

            val inputIndex = codec.dequeueInputBuffer(500_000)
            if (inputIndex < 0) return
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize <= 0) return
            val sampleTime = extractor.sampleTime
            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM)

            val deadline = System.currentTimeMillis() + DECODE_TIMEOUT_MS
            var outputFormat: MediaFormat? = null
            val info = MediaCodec.BufferInfo()

            while (System.currentTimeMillis() < deadline) {
                val outputIndex = codec.dequeueOutputBuffer(info, 100_000)
                when {
                    outputIndex >= 0 -> {
                        if (info.size <= 0 || (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            codec.releaseOutputBuffer(outputIndex, false)
                            continue
                        }

                        val image = runCatching { codec.getOutputImage(outputIndex) }.getOrNull()
                        if (image != null) {
                            try {
                                onImage(image, outputFormat ?: codec.outputFormat)
                            } finally {
                                image.close()
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        return
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    }
                    else -> {
                        Logger.w(TAG, "Unexpected dequeue result: $outputIndex")
                        return
                    }
                }
            }

            Logger.w(TAG, "Decode timeout")
        } catch (e: Exception) {
            Logger.w(TAG, "decodeOneFrameToImage failed: ${e.message}")
        }
    }

    private fun findBestDecoderForMime(mime: String): String? {
        decoderNameCache[mime]?.let { return it }
        val result = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            .sortedWith(
                compareBy<MediaCodecInfo> {
                    it.name.startsWith("OMX.google.", ignoreCase = true) ||
                        it.name.startsWith("c2.android.", ignoreCase = true)
                }.thenBy { it.name },
            )
            .firstOrNull()
            ?.name ?: return null
        decoderNameCache[mime] = result
        return result
    }

    private fun findBestGrid(count: Int): Pair<Int, Int> {
        for (cols in COLS downTo 1) {
            val rows = (count + cols - 1) / cols
            if (rows <= ROWS) return cols to rows
        }
        return COLS to ROWS
    }

    private fun readMetadata(file: File): SpriteSheetMetadata? = try {
        SpriteSheetMetadata.fromJson(file.readText())
    } catch (_: Exception) {
        null
    }
}

data class SpriteSheetMetadata(
    val cols: Int,
    val rows: Int,
    val frameCount: Int,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val intervalMs: Double,
    val durationMs: Long,
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"cols\":$cols,")
        append("\"rows\":$rows,")
        append("\"frameCount\":$frameCount,")
        append("\"thumbWidth\":$thumbWidth,")
        append("\"thumbHeight\":$thumbHeight,")
        append("\"intervalMs\":$intervalMs,")
        append("\"durationMs\":$durationMs")
        append("}")
    }

    fun saveTo(file: File) {
        file.writeText(toJson())
    }

    companion object {
        fun fromJson(json: String): SpriteSheetMetadata {
            val map = json.trim('{', '}').split(",").associate {
                val (k, v) = it.split(":", limit = 2)
                k.trim('"') to v.trim()
            }
            return SpriteSheetMetadata(
                cols = map["cols"]?.toInt() ?: 10,
                rows = map["rows"]?.toInt() ?: 10,
                frameCount = map["frameCount"]?.toInt() ?: 100,
                thumbWidth = map["thumbWidth"]?.toInt() ?: SpriteSheetGenerator.THUMB_WIDTH,
                thumbHeight = map["thumbHeight"]?.toInt() ?: SpriteSheetGenerator.THUMB_HEIGHT,
                intervalMs = map["intervalMs"]?.toDouble() ?: 0.0,
                durationMs = map["durationMs"]?.toLong() ?: 0L,
            )
        }
    }
}

data class SpriteSheetResult(
    val file: File,
    val metadata: SpriteSheetMetadata,
)
