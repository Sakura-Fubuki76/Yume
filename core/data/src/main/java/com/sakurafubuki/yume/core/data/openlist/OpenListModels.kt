package com.sakurafubuki.yume.core.data.openlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FsListRequest(
    val path: String,
    val password: String = "",
    val page: Int = 1,
    val per_page: Int = 50,
    val refresh: Boolean = false,
)

@Serializable
data class FsListResponse(
    val code: Int,
    val message: String = "",
    val data: FsListData? = null,
)

@Serializable
data class FsListData(
    val total: Int = 0,
    val content: List<FsListItem>? = null,
)

@Serializable
data class FsListItem(
    val name: String = "",
    val size: Long = 0L,
    val is_dir: Boolean = false,
    val thumb: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val modified: String = "",
    val sign: String = "",
    val type: Int = 0,
)

@Serializable
data class FsSearchRequest(
    val parent: String,
    val keywords: String,
    val scope: Int,
    val page: Int,
    val per_page: Int,
    val password: String,
)

@Serializable
data class FsSearchResponse(
    val code: Int,
    val message: String = "",
    val data: FsSearchData? = null,
)

@Serializable
data class FsSearchData(
    val total: Int = 0,
    val content: List<FsSearchItem>? = null,
)

@Serializable
data class FsSearchItem(
    val parent: String = "",
    val name: String = "",
    val fullPath: String = "",
    val modified: String = "",
    val is_dir: Boolean = false,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val type: Int = 0,
)

@Serializable
data class CloudFlareImgBedListResponse(
    val files: List<CloudFlareImgBedFile> = emptyList(),
    val directories: List<String> = emptyList(),
    val totalCount: Int = 0,
    val directFileCount: Int = 0,
    val directFolderCount: Int = 0,
    val returnedCount: Int = 0,
    val indexLastUpdated: JsonElement? = null,
    val isIndexedResponse: Boolean = true,
)

@Serializable
data class CloudFlareImgBedFile(
    val name: String = "",
    val metadata: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val code: Int,
    val message: String = "",
    val data: LoginData? = null,
)

@Serializable
data class LoginData(
    val token: String = "",
)
