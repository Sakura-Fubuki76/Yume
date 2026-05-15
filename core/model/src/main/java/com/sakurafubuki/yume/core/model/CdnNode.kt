package com.sakurafubuki.yume.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CdnNode(
    val ip: String,
    val latencyMs: Long,
)
