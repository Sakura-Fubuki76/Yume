package com.sakurafubuki.yume.core.data.repository

import java.util.LinkedHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MoovIndexCache {

    private const val MAX_ENTRIES = 32

    data class Entry(
        val keyframes: List<Mp4KeyframeExtractor.KeyframeEntry>,
        val contentLength: Long,
        val durationMs: Long?,
    )

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    private fun cacheKey(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder()
            .username("")
            .password("")
            .encodedQuery(null)
            .fragment(null)
            .build()
            .toString()
    }

    fun get(url: String): Entry? = synchronized(lock) {
        entries[cacheKey(url)]
    }

    fun put(url: String, entry: Entry) {
        synchronized(lock) {
            entries[cacheKey(url)] = entry
        }
    }

    fun findNearestKeyframe(url: String, targetTimeMs: Long): Mp4KeyframeExtractor.KeyframeEntry? {
        val entry = get(url) ?: return null
        if (entry.keyframes.isEmpty()) return null
        return entry.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - targetTimeMs) }
    }
}
