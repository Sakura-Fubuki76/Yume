package com.sakurafubuki.yume

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.sakurafubuki.yume.core.cache.ImageCacheManager
import com.sakurafubuki.yume.core.common.di.ApplicationScope
import com.sakurafubuki.yume.core.data.repository.PreferencesRepository
import com.sakurafubuki.yume.core.data.repository.WebDavServerRepository
import com.sakurafubuki.yume.core.model.ThumbnailGenerationStrategy
import com.sakurafubuki.yume.core.model.WebDavServer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.FileSystem

@Singleton
class AppImageLoaderFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val webDavServerRepository: WebDavServerRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private companion object {
        private const val IMAGE_CACHE_DIR = "image_cache"
        private const val THUMBNAILS_CACHE_DIR = "thumbnails"
        private const val VIDEO_METADATA_PROCESS_DIR = "video_metadata_process"

        private fun thumbnailCacheBytes(): Long {
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val mb = when {
                maxHeapMb < 384 -> 256
                maxHeapMb < 768 -> 512
                else -> 1024
            }
            return mb.toLong() * 1024 * 1024
        }

        private fun imageNetworkDispatcher(): okhttp3.Dispatcher {
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val maxRequests = when {
                maxHeapMb < 384 -> 3
                maxHeapMb < 768 -> 8
                maxHeapMb < 1536 -> 12
                else -> 20
            }
            val maxRequestsPerHost = when {
                maxHeapMb < 384 -> 1
                maxHeapMb < 768 -> 4
                maxHeapMb < 1536 -> 6
                else -> 10
            }
            return okhttp3.Dispatcher().apply {
                this.maxRequests = maxRequests
                this.maxRequestsPerHost = maxRequestsPerHost
            }
        }

        private fun imageConnectionPool(): okhttp3.ConnectionPool {
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val idleConnections = when {
                maxHeapMb < 384 -> 1
                maxHeapMb < 768 -> 4
                maxHeapMb < 1536 -> 6
                else -> 10
            }
            return okhttp3.ConnectionPool(idleConnections, 5, java.util.concurrent.TimeUnit.MINUTES)
        }
    }

    @Volatile
    private var webDavServersById: Map<Int, WebDavServer> = emptyMap()

    @Volatile
    private var serverIndex: Map<String, List<WebDavServer>> = emptyMap()

    init {
        applicationScope.launch(Dispatchers.IO) {
            webDavServerRepository.observeServers().collect { servers ->
                webDavServersById = servers.associateBy { it.id }
                serverIndex = servers.groupBy { server ->
                    val uri = Uri.parse(server.url)
                    val port = if (uri.port != -1) {
                        uri.port
                    } else if (uri.scheme.equals("https", ignoreCase = true)) {
                        443
                    } else {
                        80
                    }
                    "${uri.scheme}://${uri.host}:$port"
                }
            }
        }
    }

    fun create(diskCacheSizeMb: Int? = null): ImageLoader {
        val imageNetworkClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .dispatcher(imageNetworkDispatcher())
            .connectionPool(imageConnectionPool())
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("Authorization") != null) {
                    return@addInterceptor chain.proceed(request)
                }

                val authHeader = buildAuthorizationHeader(request.url)
                val authenticatedRequest = if (authHeader != null) {
                    request.newBuilder()
                        .header("Authorization", authHeader)
                        .build()
                } else {
                    request
                }
                chain.proceed(authenticatedRequest)
            }
            .authenticator { _, response ->
                val request = response.request
                if (request.header("Authorization") != null) {
                    return@authenticator null
                }
                val authHeader = buildAuthorizationHeader(request.url) ?: return@authenticator null
                request.newBuilder()
                    .header("Authorization", authHeader)
                    .build()
            }
            .build()

        val appliedCacheSizeMb = diskCacheSizeMb ?: preferencesRepository.applicationPreferences.value.diskCacheSizeMb
        val thumbnailCacheMaxBytes = thumbnailCacheBytes()
        val memoryCachePercent = preferencesRepository.applicationPreferences.value.imageBrowserMemoryCachePercent
            .coerceIn(10, 40)
        val appliedMemoryCacheBytes = ImageCacheManager.memoryCacheBytesFromRamPercent(context, memoryCachePercent)

        val localThumbnailDiskCache = lazy {
            DiskCache.Builder()
                .fileSystem(FileSystem.SYSTEM)
                .directory(context.cacheDir.resolve(THUMBNAILS_CACHE_DIR))
                .maxSizeBytes(thumbnailCacheMaxBytes)
                .build()
        }
        val remoteThumbnailDiskCache = lazy {
            DiskCache.Builder()
                .fileSystem(FileSystem.SYSTEM)
                .directory(context.cacheDir.resolve(VIDEO_METADATA_PROCESS_DIR))
                .maxSizeBytes(thumbnailCacheMaxBytes / 2)
                .build()
        }

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(appliedMemoryCacheBytes)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageNetworkClient }))
                add(
                    VideoThumbnailDecoder.Factory(
                        thumbnailStrategy = {
                            val preferences = preferencesRepository.applicationPreferences.value
                            when (preferences.thumbnailGenerationStrategy) {
                                ThumbnailGenerationStrategy.FIRST_FRAME -> ThumbnailStrategy.FirstFrame
                                ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> ThumbnailStrategy.FrameAtPercentage(preferences.thumbnailFramePosition)
                                ThumbnailGenerationStrategy.HYBRID -> ThumbnailStrategy.Hybrid(preferences.thumbnailFramePosition)
                            }
                        },
                        localThumbnailDiskCache = localThumbnailDiskCache,
                        remoteThumbnailDiskCache = remoteThumbnailDiskCache,
                        webDavServersById = { webDavServersById },
                    ),
                )
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(context.cacheDir.resolve(IMAGE_CACHE_DIR))
                    .maxSizeBytes(appliedCacheSizeMb.toLong() * 1024 * 1024)
                    .build(),
            )
            .crossfade(true)
            .build()
            .also {
                localThumbnailDiskCache.value
                ImageCacheManager.setRemoteThumbnailDiskCache(remoteThumbnailDiskCache.value)
            }
    }

    private fun buildAuthorizationHeader(url: HttpUrl): String? {
        if (isOpenListSignedResource(url)) return null
        findMatchingWebDavServer(url)?.let { matchedServer ->
            buildAuthorizationHeader(
                username = matchedServer.username,
                password = matchedServer.password,
            )?.let { return it }
        }
        return buildAuthorizationHeader(
            username = url.username,
            password = url.password,
        )
    }

    private fun isOpenListSignedResource(url: HttpUrl): Boolean = url.queryParameter("sign") != null

    private fun findMatchingWebDavServer(url: HttpUrl): WebDavServer? {
        val port = if (url.port != -1) url.port else defaultPort(url.scheme)
        val key = "${url.scheme}://${url.host}:$port"
        val candidates = serverIndex[key] ?: return null
        if (candidates.isEmpty()) return null
        if (candidates.size == 1 && candidates[0].basePath.isBlank()) return candidates[0]

        val normalizedPath = normalizePath(url.encodedPath)
        return candidates
            .asSequence()
            .filter { normalizedPath.startsWith(normalizePath(it.basePath)) }
            .maxByOrNull { normalizePath(it.basePath).length }
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "/"
        val withLeadingSlash = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return withLeadingSlash.removeSuffix("/").ifBlank { "/" }
    }

    private fun defaultPort(scheme: String): Int = if (scheme.equals("https", ignoreCase = true)) 443 else 80

    private fun buildAuthorizationHeader(username: String, password: String): String? {
        val normalizedUsername = username.trim()
        val normalizedPassword = password.trim()
        if (normalizedUsername.isBlank() && normalizedPassword.isBlank()) {
            return null
        }
        if (normalizedUsername.startsWith("Bearer ", ignoreCase = true)) {
            return normalizedUsername
        }
        if (normalizedUsername.equals("bearer", ignoreCase = true)) {
            return normalizedPassword.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        }
        return Credentials.basic(normalizedUsername, normalizedPassword)
    }
}
