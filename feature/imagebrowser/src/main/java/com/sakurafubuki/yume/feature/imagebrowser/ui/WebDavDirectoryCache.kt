package com.sakurafubuki.yume.feature.imagebrowser.ui

import com.sakurafubuki.yume.core.model.WebDavMediaItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavDirectoryCache @Inject constructor() {
    private val lock = Any()
    private val entries = object : LinkedHashMap<CacheKey, CacheEntry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?): Boolean = size > MAX_ENTRIES
    }

    fun get(serverId: Int, path: String, nowMs: Long = System.currentTimeMillis()): List<WebDavMediaItem>? {
        synchronized(lock) {
            val key = CacheKey(serverId = serverId, path = path)
            val entry = entries[key] ?: return null
            if (nowMs - entry.cachedAtMs > TTL_MS) {
                entries.remove(key)
                return null
            }
            return entry.items
        }
    }

    fun put(serverId: Int, path: String, items: List<WebDavMediaItem>, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            entries[CacheKey(serverId = serverId, path = path)] = CacheEntry(
                cachedAtMs = nowMs,
                items = items,
            )
        }
    }

    private data class CacheKey(
        val serverId: Int,
        val path: String,
    )

    private data class CacheEntry(
        val cachedAtMs: Long,
        val items: List<WebDavMediaItem>,
    )

    private companion object {
        private const val MAX_ENTRIES = 128
        private const val TTL_MS = 2 * 60 * 1000L
    }
}
