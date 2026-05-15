package com.sakurafubuki.yume.core.data.repository

import com.sakurafubuki.yume.core.data.webdav.WebDavCredentialStore
import com.sakurafubuki.yume.core.database.dao.WebDavFolderMetadataDao
import com.sakurafubuki.yume.core.database.dao.WebDavServerDao
import com.sakurafubuki.yume.core.database.dao.WebDavVideoMetadataDao
import com.sakurafubuki.yume.core.database.entities.WebDavServerEntity
import com.sakurafubuki.yume.core.model.WebDavServer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalWebDavServerRepository @Inject constructor(
    private val dao: WebDavServerDao,
    private val credentialStore: WebDavCredentialStore,
    private val videoMetadataDao: WebDavVideoMetadataDao,
    private val folderMetadataDao: WebDavFolderMetadataDao,
) : WebDavServerRepository {

    override fun observeServers(): Flow<List<WebDavServer>> = dao.observeAll().map { list ->
        list.map { entity ->
            WebDavServer(
                id = entity.id,
                name = entity.name,
                url = entity.url,
                username = entity.username,
                password = credentialStore.getPassword(entity.id),
                basePath = entity.basePath,
                createdAt = entity.createdAt,
                isImageHosting = entity.isImageHosting,
            )
        }
    }

    override suspend fun addServer(server: WebDavServer) {
        val id = dao.upsert(
            WebDavServerEntity(
                id = server.id,
                name = server.name,
                url = server.url,
                username = server.username,
                basePath = server.basePath,
                createdAt = server.createdAt,
                isImageHosting = server.isImageHosting,
            ),
        ).toInt()
        credentialStore.putPassword(id, server.password)
    }

    override suspend fun deleteServer(server: WebDavServer) {
        dao.delete(
            WebDavServerEntity(
                id = server.id,
                name = server.name,
                url = server.url,
                username = server.username,
                basePath = server.basePath,
                createdAt = server.createdAt,
                isImageHosting = server.isImageHosting,
            ),
        )
        credentialStore.deletePassword(server.id)
        videoMetadataDao.deleteByServerId(server.id)
        folderMetadataDao.deleteByServerId(server.id)
    }
}
