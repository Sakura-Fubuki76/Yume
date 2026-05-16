package com.sakurafubuki.yume.core.data.repository

import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.ChapterEntry
import java.util.LinkedHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MoovIndexCache {

    private const val TAG = "BUG4_Chapters"
    private const val MAX_ENTRIES = 32

    data class Entry(
        val keyframes: List<Mp4KeyframeExtractor.KeyframeEntry>,
        val contentLength: Long,
        val durationMs: Long?,
        val chapters: List<ChapterEntry> = emptyList(),
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
        val key = cacheKey(url)
        synchronized(lock) {
            entries[key] = entry
        }
        if (entry.chapters.isNotEmpty()) {
            Logger.d(TAG, "MoovIndexCache PUT: ${entry.chapters.size} chapters for key=${key.take(80)}")
        }
    }

    fun findNearestKeyframe(url: String, targetTimeMs: Long): Mp4KeyframeExtractor.KeyframeEntry? {
        val entry = get(url) ?: return null
        if (entry.keyframes.isEmpty()) return null
        return entry.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - targetTimeMs) }
    }

    fun getChapters(url: String): List<ChapterEntry> {
        val key = cacheKey(url)
        val result = get(url)?.chapters ?: emptyList()
        Logger.d(TAG, "MoovIndexCache GET chapters: ${result.size} entries for key=${key.take(80)} (url=${url.take(60)})")
        return result
    }
}
