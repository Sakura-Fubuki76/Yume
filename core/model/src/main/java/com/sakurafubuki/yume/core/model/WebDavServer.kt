package com.sakurafubuki.yume.core.model

data class WebDavServer(
    val id: Int = 0,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val basePath: String = "/",
    val createdAt: Long = System.currentTimeMillis(),
    val isImageHosting: Boolean = false,
)
