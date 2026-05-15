package com.sakurafubuki.yume.core.model

data class CloudFolderMetadata(
    val totalDurationMs: Long,
    val totalSize: Long,
    val mediaCount: Int,
    val folderCount: Int,
    val coverImageUri: String? = null,
    val videoCount: Int = 0,
    val imageCount: Int = 0,
)
