package com.sakurafubuki.yume.core.datastore.datasource

import androidx.datastore.core.DataStore
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.PlayerPreferences
import javax.inject.Inject

class PlayerPreferencesDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<PlayerPreferences>,
) : PreferencesDataSource<PlayerPreferences> {

    companion object {
        private const val TAG = "PlayerPreferencesDataSource"
    }

    override val preferences = preferencesDataStore.data

    override suspend fun update(transform: suspend (PlayerPreferences) -> PlayerPreferences) {
        try {
            preferencesDataStore.updateData(transform)
        } catch (ioException: Exception) {
            Logger.e(TAG, "Failed to update app preferences: $ioException")
        }
    }
}
