package com.sakurafubuki.yume.core.data.repository

import com.sakurafubuki.yume.core.model.WebDavServer
import kotlinx.coroutines.flow.Flow

interface WebDavServerRepository {
    fun observeServers(): Flow<List<WebDavServer>>
    suspend fun addServer(server: WebDavServer)
    suspend fun deleteServer(server: WebDavServer)
}
