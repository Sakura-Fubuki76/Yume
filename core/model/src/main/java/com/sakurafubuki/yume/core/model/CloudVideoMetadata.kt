package com.sakurafubuki.yume.core.model

data class CloudVideoMetadata(
    val href: String,
    val durationMs: Long,
    val thumbnailPath: String?,
    val width: Int = 0,
    val height: Int = 0,
)
