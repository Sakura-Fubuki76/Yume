package com.sakurafubuki.yume.core.model

data class LocalImage(
    val id: Long,
    val name: String,
    val uri: String,
    val width: Int,
    val height: Int,
    val dateModified: Long,
    val size: Long,
    val relativePath: String = "/",
)
