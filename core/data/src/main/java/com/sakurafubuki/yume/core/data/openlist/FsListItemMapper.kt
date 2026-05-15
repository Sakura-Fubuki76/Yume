package com.sakurafubuki.yume.core.data.openlist

import android.net.Uri
import com.sakurafubuki.yume.core.model.WebDavMediaItem
import com.sakurafubuki.yume.core.model.WebDavServer
import java.time.Instant
import java.util.Date

fun cleanThumbUrl(url: String): String = url

fun WebDavServer.toApiPath(cloudPath: String): String {
    val serverPath = Uri.decode(Uri.parse(url).path.orEmpty()).trimEnd('/')

    val davIndex = serverPath.indexOf("/dav")
    val storageBasePath = if (davIndex >= 0) {
        normalizePath(serverPath.substring(davIndex + 4))
    } else {
        serverPath
    }
    if (storageBasePath.isEmpty() || storageBasePath == "/") return normalizePath(cloudPath)
    val normalizedCloudPath = normalizePath(cloudPath)

    if (normalizedCloudPath != "/" && normalizedCloudPath.startsWith(storageBasePath)) {
        return normalizedCloudPath
    }
    return if (normalizedCloudPath == "/") {
        storageBasePath
    } else {
        normalizePath("$storageBasePath/$normalizedCloudPath")
    }
}

fun FsListItem.toWebDavMediaItem(server: WebDavServer, dirPath: String): WebDavMediaItem {
    val baseUrl = server.url.trimEnd('/')
    val serverUri = Uri.parse(server.url)
    val authority = if (serverUri.port != -1) "${serverUri.host}:${serverUri.port}" else serverUri.host.orEmpty()
    val rootBaseUrl = "${serverUri.scheme}://$authority"
    val decodedName = Uri.decode(name)
    val signParam = if (sign.isNotBlank()) "?sign=$sign" else ""

    val encodedName = Uri.encode(decodedName)

    val encodedDirSegments = dirPath.removePrefix("/")
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/") { Uri.encode(Uri.decode(it)) }
    val rawVideoUrl: String? = if (!is_dir) {
        if (server.isImageHosting) {

            if (thumb.isNotBlank() && (thumb.startsWith("http://") || thumb.startsWith("https://"))) {
                thumb
            } else if (thumb.isNotBlank()) {
                "$rootBaseUrl/${thumb.trimStart('/')}"
            } else {
                "$rootBaseUrl/$encodedDirSegments/$encodedName"
            }
        } else {
            val rawFullPath = if (dirPath == "/") "/d/$encodedName" else "/d/$encodedDirSegments/$encodedName"
            "$rootBaseUrl$rawFullPath$signParam"
        }
    } else {
        null
    }
    val (href, apiThumbnailUrl) = if (is_dir) {

        val dirUrlPath = if (dirPath == "/") {
            "$rootBaseUrl/$encodedName"
        } else {
            "$rootBaseUrl/$encodedDirSegments/$encodedName"
        }
        "$dirUrlPath/" to null
    } else {
        val resolvedHref = rawVideoUrl ?: rootBaseUrl
        val resolvedThumbnail = when {
            thumb.isNotBlank() && (thumb.startsWith("http://") || thumb.startsWith("https://")) -> {
                cleanThumbUrl(thumb)
            }
            thumb.isNotBlank() && thumb.startsWith('/') -> {
                val cleaned = cleanThumbUrl(thumb)
                if (signParam.isNotEmpty() && !cleaned.contains("?sign=")) {
                    "$rootBaseUrl$cleaned$signParam"
                } else {
                    "$rootBaseUrl$cleaned"
                }
            }
            else -> null
        }
        resolvedHref to resolvedThumbnail
    }
    return WebDavMediaItem(
        name = decodedName.ifBlank { normalizePath(name).substringAfterLast('/') },
        href = href,
        contentType = if (is_dir) "" else "",
        size = size,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        lastModified = parseOpenListModifiedTime(modified),
        isDirectory = is_dir,
        serverId = server.id,
        apiThumbnailUrl = apiThumbnailUrl,
        rawVideoUrl = rawVideoUrl,
    )
}

private fun parseOpenListModifiedTime(value: String): Date? {
    if (value.isBlank()) return null
    return runCatching { Date.from(Instant.parse(value)) }.getOrNull()
}

private fun normalizePath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return "/"
    val withLeadingSlash = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
    return withLeadingSlash.removeSuffix("/").ifBlank { "/" }
}
