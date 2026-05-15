package com.sakurafubuki.yume.feature.videopicker.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sakurafubuki.yume.core.model.MediaViewMode
import com.sakurafubuki.yume.core.ui.R

@Composable
fun MediaViewMode.name(): String = when (this) {
    MediaViewMode.VIDEOS -> stringResource(id = R.string.videos)
    MediaViewMode.FOLDERS -> stringResource(id = R.string.folders)
    MediaViewMode.FOLDER_TREE -> stringResource(id = R.string.tree)
}
