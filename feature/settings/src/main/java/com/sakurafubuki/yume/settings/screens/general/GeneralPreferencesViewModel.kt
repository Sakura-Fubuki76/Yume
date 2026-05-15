package com.sakurafubuki.yume.settings.screens.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sakurafubuki.yume.core.cache.ImageCacheManager
import com.sakurafubuki.yume.core.common.Dispatcher
import com.sakurafubuki.yume.core.common.NextDispatchers
import com.sakurafubuki.yume.core.data.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GeneralPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(GeneralPreferencesUiState())
    val uiState = uiStateInternal.asStateFlow()

    fun onEvent(event: GeneralPreferencesUiEvent) {
        when (event) {
            is GeneralPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            GeneralPreferencesUiEvent.ResetSettings -> resetSettings()
            GeneralPreferencesUiEvent.ClearThumbnailCache -> clearThumbnailCache()
        }
    }

    private fun showDialog(value: GeneralPreferencesDialog?) {
        uiStateInternal.value = uiStateInternal.value.copy(showDialog = value)
    }

    private fun resetSettings() {
        viewModelScope.launch {
            preferencesRepository.resetPreferences()
        }
    }

    private fun clearThumbnailCache() {
        viewModelScope.launch(ioDispatcher) {
            ImageCacheManager.clearThumbnailCache(context)
        }
    }
}

data class GeneralPreferencesUiState(
    val showDialog: GeneralPreferencesDialog? = null,
)

sealed interface GeneralPreferencesDialog {
    data object ResetSettingsDialog : GeneralPreferencesDialog
    data object ClearThumbnailCacheDialog : GeneralPreferencesDialog
}

sealed interface GeneralPreferencesUiEvent {
    data class ShowDialog(val value: GeneralPreferencesDialog?) : GeneralPreferencesUiEvent
    data object ResetSettings : GeneralPreferencesUiEvent
    data object ClearThumbnailCache : GeneralPreferencesUiEvent
}
