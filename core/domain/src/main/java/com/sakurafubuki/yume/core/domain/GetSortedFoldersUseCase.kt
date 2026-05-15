package com.sakurafubuki.yume.core.domain

import com.sakurafubuki.yume.core.common.Dispatcher
import com.sakurafubuki.yume.core.common.NextDispatchers
import com.sakurafubuki.yume.core.data.repository.MediaRepository
import com.sakurafubuki.yume.core.data.repository.PreferencesRepository
import com.sakurafubuki.yume.core.model.Folder
import com.sakurafubuki.yume.core.model.Sort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedFoldersUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<List<Folder>> = combine(
        mediaRepository.getFoldersFlow(),
        preferencesRepository.applicationPreferences,
    ) { folders, preferences ->

        val nonExcludedDirectories = folders.filter {
            it.mediaList.isNotEmpty() && it.path !in preferences.excludeFolders
        }

        val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
        nonExcludedDirectories.sortedWith(sort.folderComparator())
    }.flowOn(defaultDispatcher)
}
