package com.sakurafubuki.yume.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sakurafubuki.yume.core.model.Anime4KAutoDownscalePreMode
import com.sakurafubuki.yume.core.model.Anime4KRestoreMode
import com.sakurafubuki.yume.core.model.Anime4KUpscaleMode
import com.sakurafubuki.yume.core.ui.R

@Composable
fun Anime4KRestoreMode.name(): String {
    val stringRes = when (this) {
        Anime4KRestoreMode.OFF -> R.string.off
        Anime4KRestoreMode.M -> R.string.anime4k_restore_cnn_m
        Anime4KRestoreMode.L -> R.string.anime4k_restore_cnn_l
    }
    return stringResource(stringRes)
}

@Composable
fun Anime4KUpscaleMode.name(): String {
    val stringRes = when (this) {
        Anime4KUpscaleMode.OFF -> R.string.off
        Anime4KUpscaleMode.CNN_X2_M -> R.string.anime4k_upscale_cnn_x2_m
        Anime4KUpscaleMode.CNN_X2_L -> R.string.anime4k_upscale_cnn_x2_l
        Anime4KUpscaleMode.GAN_X2_M -> R.string.anime4k_upscale_gan_x2_m
    }
    return stringResource(stringRes)
}

@Composable
fun Anime4KAutoDownscalePreMode.name(): String {
    val stringRes = when (this) {
        Anime4KAutoDownscalePreMode.OFF -> R.string.off
        Anime4KAutoDownscalePreMode.X2 -> R.string.anime4k_autodownscalepre_x2
        Anime4KAutoDownscalePreMode.X4 -> R.string.anime4k_autodownscalepre_x4
    }
    return stringResource(stringRes)
}
