package com.sakurafubuki.yume.feature.player.effect

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@UnstableApi
class DitherEffect(
    private val useHdr: Boolean = false,
    private val ditherBitDepth: Int = 8
) : GlEffect {
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return DitherShaderProgram(useHdr, ditherBitDepth)
    }
}
