package com.sakurafubuki.yume.core.data.openlist

import com.sakurafubuki.yume.core.model.WebDavServer

interface OpenListApi {
    suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        page: Int,
        perPage: Int = 50,
        refresh: Boolean = false,
    ): Result<FsListData>

    suspend fun search(
        server: WebDavServer,
        parent: String,
        keywords: String = "",
        scope: Int = 2,
        page: Int = 1,
        perPage: Int = 50,
    ): Result<FsSearchData>

    suspend fun login(server: WebDavServer): Result<String>
    suspend fun probeImageDimensions(imageUrl: String): Result<Pair<Int, Int>>
}
