package com.sakurafubuki.yume.settings.extensions

import com.sakurafubuki.yume.core.model.AudioOutputMode
import com.sakurafubuki.yume.core.ui.R

val AudioOutputMode.labelRes: Int
    get() = when (this) {
        AudioOutputMode.AUDIO_TRACK -> R.string.audio_output_audiotrack
        AudioOutputMode.AAUDIO_LOW_LATENCY -> R.string.audio_output_aaudio_low_latency
        AudioOutputMode.AAUDIO_POWER_SAVING -> R.string.audio_output_aaudio_power_saving
    }
