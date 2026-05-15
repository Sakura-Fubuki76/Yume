package com.sakurafubuki.yume.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImageQuality(val compressionRatio: Float) {
    ORIGINAL(1f),
    HIGH(0.75f),
    MEDIUM(0.5f),
}
