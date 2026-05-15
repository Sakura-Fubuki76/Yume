package com.sakurafubuki.yume.core.datastore.datasource

import androidx.datastore.core.DataStore
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.SearchHistory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SearchHistoryDataSource @Inject constructor(
    private val searchHistoryDataStore: DataStore<SearchHistory>,
) {

    companion object {
        private const val TAG = "SearchHistoryDataSource"
    }

    val searchHistory: Flow<SearchHistory> = searchHistoryDataStore.data

    suspend fun update(
        transform: suspend (SearchHistory) -> SearchHistory,
    ) {
        try {
            searchHistoryDataStore.updateData(transform)
        } catch (ioException: Exception) {
            Logger.e(TAG, "Failed to update search history: $ioException")
        }
    }
}
