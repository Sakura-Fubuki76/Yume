package com.sakurafubuki.yume.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceSlider(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {},
) {
    val labelClickModifier = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    NextSegmentedListItem(
        modifier = modifier,
        onClick = {},
        onLongClick = null,
        enabled = enabled,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = {
            Column {
                description?.let {
                    Text(
                        text = description,
                        modifier = labelClickModifier,
                    )
                }
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    value = value,
                    valueRange = valueRange,
                    steps = steps,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                )
            }
        },
        content = {
            Text(
                text = title,
                modifier = labelClickModifier,
            )
        },
        trailingContent = trailingContent,
    )
}
