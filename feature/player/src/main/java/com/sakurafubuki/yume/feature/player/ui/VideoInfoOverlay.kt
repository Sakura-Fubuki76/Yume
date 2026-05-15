package com.sakurafubuki.yume.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakurafubuki.yume.feature.player.extensions.noRippleClickable
import com.sakurafubuki.yume.feature.player.state.VideoInfoState

private val OverlayBg = Color(0xDD_111111)

@Composable
fun VideoInfoOverlay(
    state: VideoInfoState,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && state.isReady,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {

        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(OverlayBg)
                .verticalScroll(rememberScrollState())
                .noRippleClickable(onClick = onDismiss)
                .padding(16.dp),
        ) {
            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.title), value = state.title)

            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.container_format), value = state.containerFormat)

            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.codec), value = state.codecName)

            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.resolution), value = state.resolutionFormatted.takeIf { it.isNotEmpty() })

            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.frame_rate), value = state.frameRateFormatted)

            InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.bitrate), value = state.bitrateFormatted)

            state.dynamicRangeFormatted?.let {
                InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.dynamic_range), value = it)
            }

            state.colorRangeFormatted?.let {
                InfoSection(title = stringResource(com.sakurafubuki.yume.core.ui.R.string.color_range), value = it)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            ),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}
