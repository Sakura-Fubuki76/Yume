package com.sakurafubuki.yume.feature.imagebrowser.ui

import com.sakurafubuki.yume.core.database.dao.ImageDimensionDao
import com.sakurafubuki.yume.core.database.entities.ImageDimensionEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ImageDimensionCache @Inject constructor(
    private val dao: ImageDimensionDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val putMutex = Mutex()

    suspend fun get(serverId: Int, uri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val entity = dao.get(serverId, uri) ?: return@withContext null
        if (entity.width <= 0 || entity.height <= 0) return@withContext null

        scope.launch { dao.upsert(entity.copy(lastAccessedAt = System.currentTimeMillis())) }
        entity.width to entity.height
    }

    suspend fun getBatch(serverId: Int, uris: List<String>): Map<String, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyMap()
        val result = mutableMapOf<String, Pair<Int, Int>>()
        val entities = dao.getByServerIdAndUris(serverId, uris)
        for (entity in entities) {
            if (entity.width > 0 && entity.height > 0) {
                result[entity.uri] = entity.width to entity.height
            }
        }
        result
    }

    fun put(serverId: Int, uri: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        scope.launch {
            putMutex.withLock {
                dao.upsertWithEviction(
                    entity = ImageDimensionEntity(
                        serverId = serverId,
                        uri = uri,
                        width = width,
                        height = height,
                        lastAccessedAt = System.currentTimeMillis(),
                    ),
                    maxEntriesPerServer = MAX_ENTRIES_PER_SERVER,
                )
            }
        }
    }

    companion object {
        private const val MAX_ENTRIES_PER_SERVER = 2000
    }
}
