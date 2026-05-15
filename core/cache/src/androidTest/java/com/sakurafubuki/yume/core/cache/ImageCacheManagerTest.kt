package com.sakurafubuki.yume.core.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImageCacheManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageCacheDir = context.cacheDir.resolve("image_cache")
    private val thumbnailCacheDir = context.cacheDir.resolve("thumbnails")

    @Before
    fun setUp() {
        cleanTestDirs()
    }

    @After
    fun tearDown() {
        cleanTestDirs()
    }

    @Test
    fun clearImageDiskCache_keeps_thumbnail_cache() {
        createCacheFile(imageCacheDir, "image.bin")
        val thumbnailFile = createCacheFile(thumbnailCacheDir, "thumb.jpg")

        ImageCacheManager.clearImageDiskCache(context)

        assertFalse(imageCacheDir.exists())
        assertTrue(thumbnailFile.exists())
    }

    @Test
    fun clearThumbnailCache_deletes_thumbnails() {
        val thumbnailFile = createCacheFile(thumbnailCacheDir, "thumb.jpg")

        ImageCacheManager.clearThumbnailCache(context)

        assertFalse(thumbnailFile.exists())
    }

    private fun createCacheFile(directory: File, fileName: String): File {
        directory.mkdirs()
        return directory.resolve(fileName).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
    }

    private fun cleanTestDirs() {
        imageCacheDir.deleteRecursively()
        thumbnailCacheDir.deleteRecursively()
    }
}
