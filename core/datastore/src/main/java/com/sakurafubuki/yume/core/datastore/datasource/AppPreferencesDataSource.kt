package com.sakurafubuki.yume.core.datastore.datasource

import androidx.datastore.core.DataStore
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.ApplicationPreferences
import javax.inject.Inject

class AppPreferencesDataSource @Inject constructor(
    private val appPreferences: DataStore<ApplicationPreferences>,
) : PreferencesDataSource<ApplicationPreferences> {

    companion object {
        private const val TAG = "AppPreferencesDataSource"
    }

    override val preferences = appPreferences.data

    override suspend fun update(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        try {
            appPreferences.updateData(transform)
        } catch (ioException: Exception) {
            Logger.e(TAG, "Failed to update app preferences: $ioException")
        }
    }
}
