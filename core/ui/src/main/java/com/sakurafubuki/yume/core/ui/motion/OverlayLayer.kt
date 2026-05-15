package com.sakurafubuki.yume.core.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun OverlayLayer(
    modifier: Modifier = Modifier,
) {
    val transitionEngine = LocalTransitionEngine.current
    val overlayContentState = LocalOverlayContentState.current
    val dimAlpha = transitionEngine.dimAlpha().coerceIn(0f, 1f)
    val overlayEntries = overlayContentState.overlayEntries

    if (dimAlpha <= 0f && overlayEntries.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = dimAlpha
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.94f),
                                Color.Black,
                                Color.Black.copy(alpha = 0.94f),
                            ),
                        ),
                    )
                    .clickable(enabled = false, onClick = {}),
            )
        }

        overlayEntries.forEach { entry ->
            entry.content.invoke(this)
        }
    }
}
