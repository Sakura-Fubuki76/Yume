package com.sakurafubuki.yume.core.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Precision
import okio.use
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LibyuvBitmapDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val reqWidth  = (options.size.width  as? Dimension.Pixels)?.px ?: return null
        val reqHeight = (options.size.height as? Dimension.Pixels)?.px ?: return null

        val bytes = source.source().use { it.readByteArray() }

        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null

        val (targetWidth, targetHeight) = if (options.precision == Precision.INEXACT) {
            val scaleW = reqWidth.toFloat() / probe.outWidth
            val scaleH = reqHeight.toFloat() / probe.outHeight
            val scale = min(scaleW, scaleH)
            max(1, (probe.outWidth * scale).toInt()) to max(1, (probe.outHeight * scale).toInt())
        } else {
            reqWidth to reqHeight
        }

        val inSampleSize = calculateInSampleSize(
            srcW = probe.outWidth, srcH = probe.outHeight,
            dstW = targetWidth,   dstH = targetHeight,
        )

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig  = Bitmap.Config.ARGB_8888
        }
        val rough = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return null

        if (isCloseEnough(rough.width, rough.height, targetWidth, targetHeight)) {
            return DecodeResult(image = rough.asImage(), isSampled = true)
        }

        val scaled = YuvToBitmapBridge.argbScale(
            srcBitmap  = rough,
            dstWidth   = targetWidth,
            dstHeight  = targetHeight,
            filterMode = FilterMode.BOX,
        )
        rough.recycle()

        if (scaled == null) return null
        return DecodeResult(image = scaled.asImage(), isSampled = true)
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        var halfW = srcW / 2
        var halfH = srcH / 2

        while (halfW >= dstW * 1.5f && halfH >= dstH * 1.5f) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }
        return sample
    }

    private fun isCloseEnough(w: Int, h: Int, targetW: Int, targetH: Int): Boolean {
        val diffW = abs(w - targetW).toFloat() / max(targetW, 1)
        val diffH = abs(h - targetH).toFloat() / max(targetH, 1)
        return diffW <= 0.1f && diffH <= 0.1f
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {

            val mime = result.mimeType ?: return null
            if (!mime.startsWith("image/") ||
                mime == "image/gif" ||
                mime == "image/svg+xml") return null

            if (options.size.width  !is Dimension.Pixels) return null
            if (options.size.height !is Dimension.Pixels) return null

            return LibyuvBitmapDecoder(result.source, options)
        }
    }
}
