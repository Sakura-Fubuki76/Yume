package com.sakurafubuki.yume.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioOutputMode {
    AUDIO_TRACK,
    AAUDIO_LOW_LATENCY,
    AAUDIO_POWER_SAVING,
}
