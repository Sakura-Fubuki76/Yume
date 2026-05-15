package com.sakurafubuki.yume.feature.player.effect

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@UnstableApi
class DebandEffect(
    private val threshold: Float = 0.008f,
    private val strength: Float = 0.004f,
    private val radius: Int = 4,
) : GlEffect {

    @UnstableApi
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        BilateralDebandShaderProgram(useHdr, threshold, strength, radius)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}
