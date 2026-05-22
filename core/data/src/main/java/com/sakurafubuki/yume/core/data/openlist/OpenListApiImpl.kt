package com.sakurafubuki.yume.core.data.openlist

import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.WebDavServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OpenListApiImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : OpenListApi {

    private val json = Json { ignoreUnknownKeys = true }

    private data class TokenEntry(val token: String, val cachedAt: Long = System.currentTimeMillis())

    private val tokenCache = mutableMapOf<String, TokenEntry>()
    private val loginMutex = Mutex()

    private fun cachedToken(baseUrl: String): String? {
        val entry = tokenCache[baseUrl] ?: return null
        if (System.currentTimeMillis() - entry.cachedAt > TOKEN_TTL_MS) {
            tokenCache.remove(baseUrl)
            return null
        }
        return entry.token
    }

    private fun cacheToken(baseUrl: String, token: String) {
        tokenCache[baseUrl] = TokenEntry(token)
    }

    override suspend fun login(server: WebDavServer): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = extractBaseUrl(server)
            val username = server.username.trim()
            val password = server.password.trim()

            if (username.isBlank() || password.isBlank()) {
                throw RuntimeException("Username and password are required for login")
            }

            val loginBody = json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password))
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(loginBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}: $bodyString")
                }

                val loginResponse = json.decodeFromString(LoginResponse.serializer(), bodyString)
                if (loginResponse.code != 200 || loginResponse.data?.token.isNullOrBlank()) {
                    throw RuntimeException(loginResponse.message.ifBlank { "Login failed" })
                }

                val token = loginResponse.data.token
                cacheToken(baseUrl, token)
                token
            }
        }
    }

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        page: Int,
        perPage: Int,
        refresh: Boolean,
    ): Result<FsListData> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = extractBaseUrl(server)
            if (server.isImageHosting) {
                return@runCatching listCloudFlareImgBedDirectory(
                    server = server,
                    baseUrl = baseUrl,
                    path = path,
                    page = page,
                    perPage = perPage,
                )
            }
            val normalizedPath = path.let {
                val trimmed = it.trim()
                if (trimmed.isEmpty()) {
                    "/"
                } else if (!trimmed.startsWith('/')) {
                    "/$trimmed"
                } else {
                    trimmed
                }
            }
            Logger.d("OpenListApi", "fs/list path=$normalizedPath server=${server.url} basePath=${server.basePath}")
            val requestBody = FsListRequest(
                path = normalizedPath,
                page = page,
                per_page = perPage,
                refresh = refresh,
            )
            val jsonBody = json.encodeToString(FsListRequest.serializer(), requestBody)

            val authHeader = resolveAuthHeader(server, baseUrl)
            var result = executeFsListRequest(baseUrl, jsonBody, authHeader)
            if (result.isAuthFailure && hasLoginCredentials(server)) {
                tokenCache.remove(baseUrl)
                val token = loginMutex.withLock {
                    cachedToken(baseUrl) ?: login(server).getOrThrow()
                }
                result = executeFsListRequest(baseUrl, jsonBody, token)
            } else if (result.isAuthFailure && authHeader != null) {
                result = executeFsListRequest(baseUrl, jsonBody, null)
            }

            result.toData()
        }
    }

    override suspend fun search(
        server: WebDavServer,
        parent: String,
        keywords: String,
        scope: Int,
        page: Int,
        perPage: Int,
    ): Result<FsSearchData> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = extractBaseUrl(server)
            if (server.isImageHosting) {
                return@runCatching searchCloudFlareImgBedImages(
                    server = server,
                    baseUrl = baseUrl,
                    parent = parent,
                    keywords = keywords,
                    page = page,
                    perPage = perPage,
                )
            }
            val normalizedParent = parent.let {
                val trimmed = it.trim()
                if (trimmed.isEmpty()) {
                    "/"
                } else if (!trimmed.startsWith('/')) {
                    "/$trimmed"
                } else {
                    trimmed
                }
            }
            val requestBody = FsSearchRequest(
                parent = normalizedParent,
                keywords = keywords,
                scope = scope,
                page = page,
                per_page = perPage,
                password = "",
            )
            val jsonBody = json.encodeToString(FsSearchRequest.serializer(), requestBody)

            val authHeader = resolveAuthHeader(server, baseUrl)
            var result = executeFsSearchRequest(baseUrl, jsonBody, authHeader)
            if (result.isAuthFailure && hasLoginCredentials(server)) {
                tokenCache.remove(baseUrl)
                val token = loginMutex.withLock {
                    cachedToken(baseUrl) ?: login(server).getOrThrow()
                }
                result = executeFsSearchRequest(baseUrl, jsonBody, token)
            } else if (result.isAuthFailure && authHeader != null) {
                result = executeFsSearchRequest(baseUrl, jsonBody, null)
            }

            result.toData()
        }
    }

    private suspend fun listCloudFlareImgBedDirectory(
        server: WebDavServer,
        baseUrl: String,
        path: String,
        page: Int,
        perPage: Int,
    ): FsListData {
        val response = executeCloudFlareImgBedListRequest(
            server = server,
            baseUrl = baseUrl,
            path = path,
            page = page,
            perPage = perPage,
            recursive = false,
            keywords = "",
        )
        return response.toFsListData(path, page)
    }

    private suspend fun searchCloudFlareImgBedImages(
        server: WebDavServer,
        baseUrl: String,
        parent: String,
        keywords: String,
        page: Int,
        perPage: Int,
    ): FsSearchData {
        val response = executeCloudFlareImgBedListRequest(
            server = server,
            baseUrl = baseUrl,
            path = parent,
            page = page,
            perPage = perPage,
            recursive = true,
            keywords = keywords,
        )
        if (!response.isIndexedResponse) {
            return searchCloudFlareImgBedImagesByDirectoryWalk(
                server = server,
                baseUrl = baseUrl,
                parent = parent,
                page = page,
                perPage = perPage,
                keywords = keywords,
                firstResponse = response,
            )
        }
        return response.toFsSearchData()
    }

    private suspend fun searchCloudFlareImgBedImagesByDirectoryWalk(
        server: WebDavServer,
        baseUrl: String,
        parent: String,
        page: Int,
        perPage: Int,
        keywords: String,
        firstResponse: CloudFlareImgBedListResponse,
    ): FsSearchData {
        val offset = (page - 1).coerceAtLeast(0) * perPage
        val collected = mutableListOf<FsSearchItem>()
        val queue = ArrayDeque<Pair<String, CloudFlareImgBedListResponse?>>()
        val visited = mutableSetOf<String>()
        var seenImages = 0

        queue.add(parent.normalizeCloudFlareImgBedPath() to firstResponse)
        val collectAll = perPage <= 0
        while (queue.isNotEmpty() && (collectAll || collected.size < perPage) && visited.size < IMAGE_HOSTING_WALK_DIRECTORY_LIMIT) {
            val (directory, cachedResponse) = queue.removeFirst()
            val normalizedDirectory = directory.normalizeCloudFlareImgBedPath()
            if (!visited.add(normalizedDirectory)) continue

            var directPage = 1
            var response = cachedResponse ?: executeCloudFlareImgBedListRequest(
                server = server,
                baseUrl = baseUrl,
                path = normalizedDirectory,
                page = directPage,
                perPage = IMAGE_HOSTING_WALK_PAGE_SIZE,
                recursive = false,
                keywords = keywords,
            )
            response.directories
                .map { it.normalizeCloudFlareImgBedPath() }
                .filterNot { it in visited }
                .forEach { queue.add(it to null) }

            while (true) {
                for (item in response.toFsSearchData().content.orEmpty().filter { it.isCloudFlareImgBedImage() }) {
                    if (seenImages++ < offset) continue
                    collected.add(item)
                    if (!collectAll && collected.size >= perPage) break
                }
                if (
                    (!collectAll && collected.size >= perPage) ||
                    !response.isIndexedResponse ||
                    directPage * IMAGE_HOSTING_WALK_PAGE_SIZE >= response.directFileCount
                ) {
                    break
                }
                directPage += 1
                response = executeCloudFlareImgBedListRequest(
                    server = server,
                    baseUrl = baseUrl,
                    path = normalizedDirectory,
                    page = directPage,
                    perPage = IMAGE_HOSTING_WALK_PAGE_SIZE,
                    recursive = false,
                    keywords = keywords,
                )
            }
        }

        return FsSearchData(
            total = firstResponse.totalCount.takeIf { it > 0 } ?: (offset + collected.size),
            content = collected,
        )
    }

    private suspend fun executeCloudFlareImgBedListRequest(
        server: WebDavServer,
        baseUrl: String,
        path: String,
        page: Int,
        perPage: Int,
        recursive: Boolean,
        keywords: String,
    ): CloudFlareImgBedListResponse {
        val authHeader = resolveAuthHeader(server, baseUrl)
        val endpoint = if (authHeader.isNullOrBlank()) {
            "$baseUrl/api/public/list"
        } else {
            "$baseUrl/api/manage/list"
        }
        val urlBuilder = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("dir", path.toCloudFlareImgBedDir())
            .addQueryParameter("start", ((page - 1).coerceAtLeast(0) * perPage).toString())
            .addQueryParameter("count", perPage.toString())
            .addQueryParameter("recursive", recursive.toString())
            .addQueryParameter(if (authHeader.isNullOrBlank()) "type" else "fileType", "image")

        if (!authHeader.isNullOrBlank()) {
            urlBuilder.addQueryParameter("accessStatus", "normal")
        }
        if (keywords.isNotBlank()) {
            urlBuilder.addQueryParameter("search", keywords)
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
        authHeader?.let { requestBuilder.header("Authorization", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${bodyString.toPreview()}")
            }
            if (!bodyString.isLikelyJson()) {
                throw RuntimeException("CloudFlare ImgBed API returned a non-JSON response: ${bodyString.toPreview()}")
            }
            return runCatching { json.decodeFromString(CloudFlareImgBedListResponse.serializer(), bodyString) }
                .getOrElse {
                    throw RuntimeException("CloudFlare ImgBed list response could not be parsed: ${bodyString.toPreview()}", it)
                }
        }
    }

    private fun executeFsListRequest(
        baseUrl: String,
        jsonBody: String,
        authHeader: String?,
    ): FsListResult {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/fs/list")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")

        authHeader?.let { requestBuilder.header("Authorization", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            val parsed = if (response.isSuccessful && bodyString.isLikelyJson()) {
                runCatching { json.decodeFromString(FsListResponse.serializer(), bodyString) }
                    .getOrElse {
                        throw RuntimeException("OpenList API response could not be parsed: ${bodyString.toPreview()}", it)
                    }
            } else if (response.isSuccessful) {
                throw RuntimeException("OpenList API returned a non-JSON response: ${bodyString.toPreview()}")
            } else {
                null
            }
            return FsListResult(response.code, bodyString, parsed)
        }
    }

    private fun executeFsSearchRequest(
        baseUrl: String,
        jsonBody: String,
        authHeader: String?,
    ): FsSearchResult {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/fs/search")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")

        authHeader?.let { requestBuilder.header("Authorization", it) }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            val parsed = if (response.isSuccessful && bodyString.isLikelyJson()) {
                runCatching { json.decodeFromString(FsSearchResponse.serializer(), bodyString) }
                    .getOrElse {
                        throw RuntimeException("OpenList search response could not be parsed: ${bodyString.toPreview()}", it)
                    }
            } else if (response.isSuccessful) {
                throw RuntimeException("OpenList search returned a non-JSON response: ${bodyString.toPreview()}")
            } else {
                null
            }
            return FsSearchResult(response.code, bodyString, parsed)
        }
    }

    private suspend fun resolveAuthHeader(server: WebDavServer, baseUrl: String): String? {
        buildAuthorizationHeader(server)?.let { return it }
        cachedToken(baseUrl)?.let { return it }
        if (!hasLoginCredentials(server)) return null

        val token = loginMutex.withLock {
            cachedToken(baseUrl) ?: login(server).getOrThrow()
        }
        return token
    }

    private fun buildAuthorizationHeader(server: WebDavServer): String? {
        val username = server.username.trim()
        val password = server.password.trim()
        if (username.isBlank() && password.isBlank()) return null
        if (username.startsWith("Bearer ", ignoreCase = true)) {
            val token = username.substringAfter(' ').trim().takeIf { it.isNotBlank() } ?: return null
            return if (server.isImageHosting) "Bearer $token" else token
        }
        if (username.equals("bearer", ignoreCase = true)) {
            val token = password.takeIf { it.isNotBlank() } ?: return null
            return if (server.isImageHosting) "Bearer $token" else token
        }
        if (server.isImageHosting && username.isNotBlank() && password.isNotBlank()) {
            return Credentials.basic(username, password)
        }
        return null
    }

    private fun hasLoginCredentials(server: WebDavServer): Boolean {
        if (server.isImageHosting) return false
        val username = server.username.trim()
        val password = server.password.trim()
        return username.isNotBlank() &&
            password.isNotBlank() &&
            !username.startsWith("Bearer ", ignoreCase = true) &&
            !username.equals("bearer", ignoreCase = true)
    }

    private fun extractBaseUrl(server: WebDavServer): String {
        val serverUri = server.url.toUri()
        val authority = if (serverUri.port != -1) "${serverUri.host}:${serverUri.port}" else serverUri.host.orEmpty()
        return "${serverUri.scheme}://$authority"
    }

    override suspend fun probeImageDimensions(imageUrl: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(imageUrl)
                .header("Range", "bytes=0-65535")
                .header("Accept", "image/*")
                .build()

            val data = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}")
                }
                val body = response.body ?: throw RuntimeException("Empty response body")
                body.bytes()
            }
            if (data.isEmpty()) {
                throw RuntimeException("Zero-length response")
            }

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)

            if (opts.outWidth > 0 && opts.outHeight > 0) {
                opts.outWidth to opts.outHeight
            } else {
                throw RuntimeException("BitmapFactory failed to decode dimensions")
            }
        }
    }

    private data class FsListResult(
        val httpCode: Int,
        val body: String,
        val response: FsListResponse?,
    ) {
        val isAuthFailure: Boolean
            get() = httpCode == 401 || httpCode == 403 || response?.code == 401

        fun toData(): FsListData {
            if (httpCode !in 200..299) {
                throw RuntimeException("HTTP $httpCode: $body")
            }
            val fsResponse = response ?: throw RuntimeException("Empty API response")
            if (fsResponse.code != 200) {
                throw RuntimeException(fsResponse.message.ifBlank { "API error code: ${fsResponse.code}" })
            }
            return fsResponse.data ?: FsListData()
        }
    }

    private data class FsSearchResult(
        val httpCode: Int,
        val body: String,
        val response: FsSearchResponse?,
    ) {
        val isAuthFailure: Boolean
            get() = httpCode == 401 || httpCode == 403 || response?.code == 401

        fun toData(): FsSearchData {
            if (httpCode !in 200..299) {
                throw RuntimeException("HTTP $httpCode: $body")
            }
            val searchResponse = response ?: throw RuntimeException("Empty search response")
            if (searchResponse.code != 200) {
                throw RuntimeException(searchResponse.message.ifBlank { "Search API error code: ${searchResponse.code}" })
            }
            return searchResponse.data ?: FsSearchData()
        }
    }

    private fun String.isLikelyJson(): Boolean {
        val first = trimStart().firstOrNull()
        return first == '{' || first == '['
    }

    private fun String.toPreview(maxLength: Int = 240): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return "<empty body>"
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    private companion object {
        private const val TOKEN_TTL_MS = 30 * 60 * 1000L
        private const val IMAGE_HOSTING_WALK_PAGE_SIZE = 50
        private const val IMAGE_HOSTING_WALK_DIRECTORY_LIMIT = 200
    }
}

private fun CloudFlareImgBedListResponse.toFsListData(path: String, page: Int): FsListData {
    val normalizedPath = path.normalizeCloudFlareImgBedPath()
    val directoryItems = if (page <= 1) {
        directories.mapNotNull { directory ->
            val directoryPath = directory.normalizeCloudFlareImgBedPath()
            val name = directoryPath.substringAfterLast('/').ifBlank { return@mapNotNull null }
            FsListItem(
                name = name,
                is_dir = true,
                modified = indexLastUpdated.jsonScalarString().orEmpty(),
            )
        }
    } else {
        emptyList()
    }
    val fileItems = files.mapNotNull { file ->
        val fullPath = file.name.normalizeCloudFlareImgBedPath()
        val parent = fullPath.substringBeforeLast('/', "/").ifBlank { "/" }
        if (parent != normalizedPath) return@mapNotNull null
        val name = fullPath.substringAfterLast('/').ifBlank { return@mapNotNull null }
        FsListItem(
            name = name,
            size = file.metadata.metadataFileSizeBytes(),
            is_dir = false,
            thumb = fullPath.toCloudFlareImgBedFilePath(),
            width = file.metadata.metadataInt("Width", "ImageWidth", "Image-Width"),
            height = file.metadata.metadataInt("Height", "ImageHeight", "Image-Height"),
            modified = file.metadata.metadataString("TimeStamp"),
            type = file.metadata.metadataString("FileType", "File-Mime").toOpenListType(),
        )
    }
    val directTotal = when {
        directFileCount > 0 || directFolderCount > 0 -> directFileCount + directFolderCount
        else -> directoryItems.size + fileItems.size
    }
    return FsListData(
        total = directTotal,
        content = directoryItems + fileItems,
    )
}

private fun CloudFlareImgBedListResponse.toFsSearchData(): FsSearchData {
    val items = files.mapNotNull { file ->
        val fullPath = file.name.normalizeCloudFlareImgBedPath()
        val name = fullPath.substringAfterLast('/').ifBlank { return@mapNotNull null }
        val parent = fullPath.substringBeforeLast('/', "/").ifBlank { "/" }
        FsSearchItem(
            parent = parent,
            name = name,
            fullPath = fullPath,
            modified = file.metadata.metadataString("TimeStamp"),
            is_dir = false,
            size = file.metadata.metadataFileSizeBytes(),
            width = file.metadata.metadataInt("Width", "ImageWidth", "Image-Width"),
            height = file.metadata.metadataInt("Height", "ImageHeight", "Image-Height"),
            type = file.metadata.metadataString("FileType", "File-Mime").toOpenListType(),
        )
    }
    return FsSearchData(
        total = totalCount.takeIf { it > 0 } ?: returnedCount.takeIf { it > 0 } ?: items.size,
        content = items,
    )
}

private fun FsSearchItem.isCloudFlareImgBedImage(): Boolean {
    if (is_dir) return false
    if (type == 5) return true
    val extension = name
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/', name)
        .substringAfterLast('.', "")
        .lowercase()
    return extension in CLOUD_FLARE_IMAGE_EXTENSIONS
}

private fun String.toCloudFlareImgBedDir(): String {
    val path = normalizeCloudFlareImgBedPath().removePrefix("/")
    return if (path.isBlank()) "" else path.removeSuffix("/") + "/"
}

private fun String.normalizeCloudFlareImgBedPath(): String {
    val trimmed = trim().replace('\\', '/').replace(Regex("/{2,}"), "/")
    if (trimmed.isEmpty()) return "/"
    val withoutTrailingSlash = trimmed.removeSuffix("/")
    val withLeadingSlash = if (withoutTrailingSlash.startsWith('/')) withoutTrailingSlash else "/$withoutTrailingSlash"
    return withLeadingSlash.ifBlank { "/" }
}

private fun String.toCloudFlareImgBedFilePath(): String = "/file/" + removePrefix("/")

private fun Map<String, JsonElement>.metadataString(vararg keys: String): String {
    for (key in keys) {
        get(key)?.jsonScalarString()?.let { return it }
    }
    val normalizedKeys = keys.map { it.metadataKeyToken() }
    return entries.firstOrNull { (key, _) -> key.metadataKeyToken() in normalizedKeys }
        ?.value
        .jsonScalarString()
        .orEmpty()
}

private fun Map<String, JsonElement>.metadataLong(vararg keys: String): Long = metadataString(*keys).toLongOrNull() ?: 0L

private fun Map<String, JsonElement>.metadataInt(vararg keys: String): Int = metadataString(*keys).toIntOrNull() ?: 0

private fun Map<String, JsonElement>.metadataFileSizeBytes(): Long {
    metadataLong("FileSizeBytes", "File-Size-Bytes").takeIf { it > 0L }?.let { return it }
    val raw = metadataString("FileSize", "File-Size")
    raw.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
    val numeric = raw.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
    return (numeric * 1024.0 * 1024.0).toLong().coerceAtLeast(1L)
}

private fun JsonElement?.jsonScalarString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.contentOrNull
        ?: primitive.longOrNull?.toString()
        ?: primitive.booleanOrNull?.toString()
}

private fun String.metadataKeyToken(): String = lowercase().filter { it.isLetterOrDigit() }

private fun String.toOpenListType(): Int = when {
    startsWith("image/", ignoreCase = true) -> 5
    startsWith("video/", ignoreCase = true) -> 2
    else -> 0
}

private val CLOUD_FLARE_IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "tif", "tiff",
)
