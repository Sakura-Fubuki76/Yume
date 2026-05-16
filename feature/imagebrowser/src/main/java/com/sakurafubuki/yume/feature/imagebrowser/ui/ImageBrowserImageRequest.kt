package com.sakurafubuki.yume.feature.imagebrowser.ui

import android.content.Context
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import com.sakurafubuki.yume.core.cache.CacheTimestampStore
import com.sakurafubuki.yume.core.model.ApplicationPreferences
import com.sakurafubuki.yume.core.model.ImageQuality

internal enum class ImageRequestProfile {
    THUMBNAIL,
    VIEWER,
    PREFETCH,
}

internal fun stableCacheKey(data: Any): String {
    val raw = data.toString()
    val stable = raw.substringBefore('?')
    return if (stable.startsWith("http://") || stable.startsWith("https://")) {
        stable
    } else {
        raw
    }
}

internal fun buildImageRequest(
    context: Context,
    data: Any,
    quality: ImageQuality,
    profile: ImageRequestProfile = ImageRequestProfile.VIEWER,
    thumbnailMaxEdgePx: Int = ApplicationPreferences.DEFAULT_IMAGE_BROWSER_THUMBNAIL_SIZE_PX,
): ImageRequest {
    val cacheKeySuffix = when (profile) {
        ImageRequestProfile.THUMBNAIL -> "thumb"
        ImageRequestProfile.VIEWER -> "viewer"
        ImageRequestProfile.PREFETCH -> "prefetch"
    }
    val isRemote = isRemoteImageData(data)
    val isImageHosting = isRemote &&
        ImageViewerStore.imageHostingBaseUrls.any { baseUrl ->
            data.toString().startsWith(baseUrl, ignoreCase = true)
        }
    val cacheKey = if (isRemote) stableCacheKey(data) else data.toString()
    return ImageRequest.Builder(context)
        .data(data)
        .crossfade(false)
        .memoryCacheKey("$cacheKey|$quality|$cacheKeySuffix|$thumbnailMaxEdgePx")
        .apply {
            if (isRemote && !isImageHosting) {
                diskCacheKey("$cacheKey|$quality|$cacheKeySuffix|$thumbnailMaxEdgePx")
            } else {
                diskCachePolicy(CachePolicy.DISABLED)

                memoryCachePolicy(CachePolicy.ENABLED)
            }
        }
        .applyImageQuality(
            quality = quality,
            profile = profile,
            thumbnailMaxEdgePx = thumbnailMaxEdgePx,
        )
        .apply {
            if (profile != ImageRequestProfile.VIEWER || quality != ImageQuality.ORIGINAL) {
                allowRgb565(true)
            }
        }
        .listener(
            onSuccess = { _, result ->
                if (isRemote && !isImageHosting && result.dataSource == coil3.decode.DataSource.NETWORK) {
                    CacheTimestampStore.record(context, cacheKey)
                }
            },
        )
        .build()
}

internal fun thumbnailMemoryCacheKey(
    data: Any,
    quality: ImageQuality,
    thumbnailMaxEdgePx: Int = ApplicationPreferences.DEFAULT_IMAGE_BROWSER_THUMBNAIL_SIZE_PX,
): String {
    val cacheKey = if (isRemoteImageData(data)) stableCacheKey(data) else data.toString()
    return "$cacheKey|$quality|thumb|$thumbnailMaxEdgePx"
}

internal fun isRemoteImageData(data: Any): Boolean {
    val scheme = when (data) {
        is android.net.Uri -> data.scheme
        is String -> data.toUri().scheme
        else -> null
    }?.lowercase()
    return scheme == "http" || scheme == "https"
}

internal fun resolveImageLoader(
    context: Context,
    data: Any,
    localImageLoader: ImageLoader,
): ImageLoader = if (isRemoteImageData(data)) {
    SingletonImageLoader.get(context)
} else {
    localImageLoader
}

private fun ImageRequest.Builder.applyImageQuality(
    quality: ImageQuality,
    profile: ImageRequestProfile,
    thumbnailMaxEdgePx: Int,
): ImageRequest.Builder {
    if (quality == ImageQuality.ORIGINAL && profile == ImageRequestProfile.VIEWER) {
        size(Size.ORIGINAL)
        precision(Precision.EXACT)
        return this
    }
    if (
        thumbnailMaxEdgePx == ApplicationPreferences.IMAGE_BROWSER_THUMBNAIL_SIZE_ORIGINAL &&
        profile != ImageRequestProfile.VIEWER
    ) {
        size(Size.ORIGINAL)
        precision(Precision.EXACT)
        return this
    }
    val thumbnailSizePx = thumbnailMaxEdgePx
        .takeIf { it > 0 }
        ?: ApplicationPreferences.DEFAULT_IMAGE_BROWSER_THUMBNAIL_SIZE_PX
    val (baseWidth, baseHeight) = when (profile) {
        ImageRequestProfile.THUMBNAIL -> thumbnailSizePx to thumbnailSizePx
        ImageRequestProfile.PREFETCH -> (thumbnailSizePx * 3 / 2) to (thumbnailSizePx * 3 / 2)
        ImageRequestProfile.VIEWER -> 1600 to 1600
    }
    val ratio = quality.compressionRatio
    val minEdge = if (profile == ImageRequestProfile.VIEWER) 640 else 256
    size(
        width = (baseWidth * ratio).toInt().coerceAtLeast(minEdge),
        height = (baseHeight * ratio).toInt().coerceAtLeast(minEdge),
    )
    precision(if (profile == ImageRequestProfile.VIEWER) Precision.EXACT else Precision.INEXACT)
    return this
}
