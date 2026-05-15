package com.sakurafubuki.yume.core.data.webdav

import com.sakurafubuki.yume.core.model.WebDavMediaItem
import com.sakurafubuki.yume.core.model.WebDavServer
import java.io.InputStream

interface WebDavRepository {
    suspend fun listDirectory(server: WebDavServer, path: String): List<WebDavMediaItem>
    suspend fun fileExists(server: WebDavServer, path: String): Boolean
    suspend fun downloadFile(server: WebDavServer, path: String): InputStream?
    suspend fun testConnection(server: WebDavServer): Result<Unit>
    fun getStreamUrl(item: WebDavMediaItem, server: WebDavServer): String
}
