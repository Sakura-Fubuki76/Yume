package com.sakurafubuki.yume.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.sakurafubuki.yume.core.ui.designsystem.NextIcons
import com.sakurafubuki.yume.core.ui.R

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClickablePreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    trailingContent: @Composable () -> Unit = {},
) {
    PreferenceItem(
        title = title,
        description = description,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        trailingContent = trailingContent,
    )
}

@Preview
@Composable
private fun ClickablePreferenceItemPreview() {
    ClickablePreferenceItem(
        title = stringResource(R.string.title),
        description = stringResource(R.string.media_library_description),
        icon = NextIcons.DoubleTap,
        onClick = {},
        enabled = false,
    )
}
