package com.sakurafubuki.yume.core.data.openlist

import kotlinx.serialization.Serializable

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
    val is_dir: Boolean = false,
    val size: Long = 0L,
    val type: Int = 0,
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
