package com.sakurafubuki.yume.core.model

import java.util.Date

data class WebDavMediaItem(
    val name: String,
    val href: String,
    val contentType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val lastModified: Date?,
    val isDirectory: Boolean,
    val serverId: Int,

) {
    val isVideo: Boolean
        get() = contentType.startsWith("video/") || extension in VIDEO_EXTENSIONS

    val isImage: Boolean
        get() = contentType.startsWith("image/") || extension in IMAGE_EXTENSIONS

    private val extension: String
        get() {
            val source = name.ifBlank { href }
            val normalized = source
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .lowercase()
            return normalized.substringAfterLast('.', "")
        }

    val streamUrl: String
        get() = href

    private companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "m4v", "flv", "wmv", "ts", "m2ts", "3gp", "mpg", "mpeg",
        )
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
        )
    }
}
