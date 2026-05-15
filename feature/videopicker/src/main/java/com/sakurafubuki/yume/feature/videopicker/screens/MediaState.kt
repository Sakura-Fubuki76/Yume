package com.sakurafubuki.yume.feature.videopicker.screens

import com.sakurafubuki.yume.core.model.Folder

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
