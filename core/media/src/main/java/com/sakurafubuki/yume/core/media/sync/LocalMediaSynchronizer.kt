package com.sakurafubuki.yume.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.provider.MediaStore
import coil3.ImageLoader
import com.sakurafubuki.yume.core.common.Dispatcher
import com.sakurafubuki.yume.core.common.NextDispatchers
import com.sakurafubuki.yume.core.common.di.ApplicationScope
import com.sakurafubuki.yume.core.common.extensions.VIDEO_COLLECTION_URI
import com.sakurafubuki.yume.core.common.extensions.getStorageVolumes
import com.sakurafubuki.yume.core.common.extensions.prettyName
import com.sakurafubuki.yume.core.common.extensions.scanPaths
import com.sakurafubuki.yume.core.common.extensions.scanStorage
import com.sakurafubuki.yume.core.database.converter.UriListConverter
import com.sakurafubuki.yume.core.database.dao.DirectoryDao
import com.sakurafubuki.yume.core.database.dao.MediumDao
import com.sakurafubuki.yume.core.database.dao.MediumStateDao
import com.sakurafubuki.yume.core.database.entities.DirectoryEntity
import com.sakurafubuki.yume.core.database.entities.MediumEntity
import com.sakurafubuki.yume.core.media.model.MediaVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.LinkedHashSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class LocalMediaSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
    private val directoryDao: DirectoryDao,
    private val imageLoader: ImageLoader,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean = path?.let { context.scanPaths(listOf(path)) }
        ?: context.getStorageVolumes().all { context.scanStorage(it.path) }

    override fun startSync() {
        if (mediaSyncingJob != null) return
        mediaSyncingJob = getMediaVideosFlow().onEach { media ->
            updateDirectories(media)
            updateMedia(media)
        }.launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
    }

    private suspend fun updateDirectories(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val storageRootPaths = context.getStorageVolumes().map { it.path }
        val directories = buildDirectoryEntities(
            media = media,
            storageRootPaths = storageRootPaths,
        )
        directoryDao.upsertAll(directories)

        val currentDirectoryPaths = directories.map { it.path }.toSet()

        val unwantedDirectories = directoryDao.getAll().first()
            .filterNot { it.path in currentDirectoryPaths }

        val unwantedDirectoriesPaths = unwantedDirectories.map { it.path }

        directoryDao.delete(unwantedDirectoriesPaths)
    }

    private fun buildDirectoryEntities(
        media: List<MediaVideo>,
        storageRootPaths: List<String>,
    ): List<DirectoryEntity> {
        if (media.isEmpty() || storageRootPaths.isEmpty()) return emptyList()

        val normalizedRoots = storageRootPaths.map { normalizePath(it) }
        val orderedDirectoryPaths = LinkedHashSet<String>()

        media.forEach { mediaVideo ->
            var current = File(mediaVideo.data).parentFile
            while (current != null) {
                val currentPath = normalizePath(current.path)
                val rootPath = findOwningRootPath(currentPath, normalizedRoots) ?: break
                orderedDirectoryPaths.add(currentPath)
                if (currentPath == rootPath) break
                current = current.parentFile
            }
        }

        return orderedDirectoryPaths.map { directoryPath ->
            val rootPath = findOwningRootPath(directoryPath, normalizedRoots)
            val file = File(directoryPath)
            val parentPath = when {
                rootPath == null || directoryPath == rootPath -> "/"
                else -> file.parentFile?.path?.let(::normalizePath) ?: "/"
            }
            DirectoryEntity(
                path = directoryPath,
                name = file.prettyName,
                modified = file.lastModified(),
                parentPath = parentPath,
            )
        }
    }

    private fun findOwningRootPath(path: String, normalizedRoots: List<String>): String? = normalizedRoots.firstOrNull { root ->
        path == root || path.startsWith("$root/")
    }

    private fun normalizePath(path: String): String = path.trimEnd('/').ifBlank { "/" }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val existingMedia = mediumDao.getAll().first()
        val existingMediaByUri = existingMedia.associateBy { it.uriString }
        val existingMediaStatesByUri = mediumStateDao.getAll().first().associateBy { it.uriString }

        val mediumEntities = media.map {
            val file = File(it.data)
            val mediumEntity = existingMediaByUri[it.uri.toString()]
            mediumEntity?.copy(
                path = file.path,
                name = file.name,
                size = it.size,
                width = it.width,
                height = it.height,
                duration = it.duration,
                mediaStoreId = it.id,
                modified = it.dateModified,
                parentPath = file.parent!!,
            ) ?: MediumEntity(
                uriString = it.uri.toString(),
                path = it.data,
                name = file.name,
                parentPath = file.parent!!,
                modified = it.dateModified,
                size = it.size,
                width = it.width,
                height = it.height,
                duration = it.duration,
                mediaStoreId = it.id,
            )
        }

        mediumDao.upsertAll(mediumEntities)

        val currentMediaUris = mediumEntities.map { it.uriString }.toSet()

        val unwantedMedia = existingMedia.filterNot { it.uriString in currentMediaUris }

        val unwantedMediaUris = unwantedMedia.map { it.uriString }

        mediumDao.delete(unwantedMediaUris)
        mediumStateDao.delete(unwantedMediaUris)

        unwantedMedia.forEach { mediumEntity ->
            try {
                imageLoader.diskCache?.remove(mediumEntity.uriString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val currentMediaExternalSubs = currentMediaUris.flatMap { uriString ->
            val mediumState = existingMediaStatesByUri[uriString] ?: return@flatMap emptyList<String>()
            UriListConverter.fromStringToList(mediumState.externalSubs)
        }.toSet()

        unwantedMedia.forEach { mediumEntity ->
            val mediumState = existingMediaStatesByUri[mediumEntity.uriString] ?: return@forEach
            for (sub in UriListConverter.fromStringToList(mediumState.externalSubs)) {
                if (sub !in currentMediaExternalSubs) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
    ): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getMediaVideo(selection, selectionArgs, sortOrder))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)

        trySend(getMediaVideo(selection, selectionArgs, sortOrder))

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(dispatcher).distinctUntilChanged().conflate()

    private fun getMediaVideo(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                mediaVideos.add(
                    MediaVideo(
                        id = id,
                        data = cursor.getString(dataColumn),
                        duration = cursor.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                    ),
                )
            }
        }
        return mediaVideos.filter { File(it.data).exists() }
    }

    companion object {
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }
}
