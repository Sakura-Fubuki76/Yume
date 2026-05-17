package com.sakurafubuki.yume.settings.extensions

import com.sakurafubuki.yume.core.model.Anime4KAutoDownscalePreMode
import com.sakurafubuki.yume.core.model.Anime4KRestoreMode
import com.sakurafubuki.yume.core.model.Anime4KUpscaleMode

fun Anime4KRestoreMode.name(): String = when (this) {
    Anime4KRestoreMode.OFF -> "Off"
    Anime4KRestoreMode.L -> "Anime4K Restore CNN L"
    Anime4KRestoreMode.M -> "Anime4K Restore CNN M"
}

fun Anime4KUpscaleMode.name(): String = when (this) {
    Anime4KUpscaleMode.OFF -> "Off"
    Anime4KUpscaleMode.CNN_X2_L -> "Anime4K Upscale CNN x2 L"
    Anime4KUpscaleMode.CNN_X2_M -> "Anime4K Upscale CNN x2 M"
    Anime4KUpscaleMode.GAN_X2_M -> "Anime4K Upscale GAN x2 M"
}

fun Anime4KAutoDownscalePreMode.name(): String = when (this) {
    Anime4KAutoDownscalePreMode.OFF -> "Off"
    Anime4KAutoDownscalePreMode.X2 -> "AutoDownscalePre x2"
    Anime4KAutoDownscalePreMode.X4 -> "AutoDownscalePre x4"
}
