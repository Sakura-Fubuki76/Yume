package com.sakurafubuki.yume.core.data.repository

import com.sakurafubuki.yume.core.model.ApplicationPreferences
import com.sakurafubuki.yume.core.model.PlayerPreferences
import kotlinx.coroutines.flow.StateFlow

interface PreferencesRepository {

    val applicationPreferences: StateFlow<ApplicationPreferences>

    val playerPreferences: StateFlow<PlayerPreferences>

    suspend fun updateApplicationPreferences(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    )

    suspend fun updatePlayerPreferences(transform: suspend (PlayerPreferences) -> PlayerPreferences)

    suspend fun resetPreferences()
}
