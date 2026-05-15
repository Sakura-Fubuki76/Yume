package com.sakurafubuki.yume.core.domain

import com.sakurafubuki.yume.core.common.Dispatcher
import com.sakurafubuki.yume.core.common.NextDispatchers
import com.sakurafubuki.yume.core.model.Folder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class GetPopularFoldersUseCase @Inject constructor(
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(limit: Int = 5): Flow<List<Folder>> = getSortedFoldersUseCase().map { folders ->
        folders.sortedWith(
            compareByDescending<Folder> { folder ->
                folder.allMediaList.count { it.lastPlayedAt != null }
            }.thenByDescending { folder ->
                folder.recentlyPlayedVideo?.lastPlayedAt?.time ?: 0L
            }.thenByDescending { folder ->
                folder.mediaList.size
            },
        ).take(limit)
    }.flowOn(defaultDispatcher)
}
