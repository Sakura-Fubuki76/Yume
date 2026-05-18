package com.sakurafubuki.yume.core.media.sync

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import com.sakurafubuki.yume.core.common.Dispatcher
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.common.NextDispatchers
import com.sakurafubuki.yume.core.common.di.ApplicationScope
import com.sakurafubuki.yume.core.database.dao.MediumDao
import com.sakurafubuki.yume.core.database.dao.WebDavVideoMetadataDao
import com.sakurafubuki.yume.core.database.entities.AudioStreamInfoEntity
import com.sakurafubuki.yume.core.database.entities.SubtitleStreamInfoEntity
import com.sakurafubuki.yume.core.database.entities.VideoStreamInfoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.sakurafubuki.yume.nativelib.mediainfo.AudioStream
import io.github.sakurafubuki.yume.nativelib.mediainfo.MediaInfoBuilder
import io.github.sakurafubuki.yume.nativelib.mediainfo.SubtitleStream
import io.github.sakurafubuki.yume.nativelib.mediainfo.VideoStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalMediaInfoSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val webDavVideoMetadataDao: WebDavVideoMetadataDao,
    private val imageLoader: ImageLoader,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val dispatcher: CoroutineDispatcher,
) : MediaInfoSynchronizer {

    private val activeSyncJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    override fun sync(uri: Uri) {
        applicationScope.launch(dispatcher) {
            val uriString = uri.toString()
            val currentJob = currentCoroutineContext()[Job] ?: return@launch

            while (true) {
                val existingJob = mutex.withLock {
                    val activeJob = activeSyncJobs[uriString]
                    if (activeJob == null) {
                        activeSyncJobs[uriString] = currentJob
                    }
                    activeJob
                }
                if (existingJob == null) break
                existingJob.join()
            }

            try {
                performSync(uri)
            } finally {
                mutex.withLock {
                    if (activeSyncJobs[uriString] == currentJob) {
                        activeSyncJobs.remove(uriString)
                    }
                }
            }
        }
    }

    override suspend fun clearThumbnailsCache() {
        context.cacheDir.resolve("thumbnails").deleteRecursively()
        context.cacheDir.resolve("video_metadata_process").deleteRecursively()
        imageLoader.memoryCache?.clear()
        webDavVideoMetadataDao.clearAllThumbnailPaths()
    }

    private suspend fun performSync(uri: Uri) {
        val medium = mediumDao.getWithInfo(uri.toString()) ?: return
        if (medium.videoStreamInfo != null) return

        val mediaInfo = runCatching {
            MediaInfoBuilder().from(context = context, uri = uri).build() ?: throw NullPointerException()
        }.onFailure { e ->
            e.printStackTrace()
            Logger.d(TAG, "sync: MediaInfoBuilder exception", e)
        }.getOrNull() ?: return

        try {
            val videoStreamInfo = mediaInfo.videoStream?.toVideoStreamInfoEntity(medium.mediumEntity.uriString)
            val audioStreamsInfo = mediaInfo.audioStreams.map {
                it.toAudioStreamInfoEntity(medium.mediumEntity.uriString)
            }
            val subtitleStreamsInfo = mediaInfo.subtitleStreams.map {
                it.toSubtitleStreamInfoEntity(medium.mediumEntity.uriString)
            }

            mediumDao.upsert(medium.mediumEntity.copy(format = mediaInfo.format))
            videoStreamInfo?.let { mediumDao.upsertVideoStreamInfo(it) }
            audioStreamsInfo.onEach { mediumDao.upsertAudioStreamInfo(it) }
            subtitleStreamsInfo.onEach { mediumDao.upsertSubtitleStreamInfo(it) }
        } finally {
            runCatching { mediaInfo.release() }
        }
    }

    companion object {
        private const val TAG = "MediaInfoSynchronizer"
    }
}

private fun VideoStream.toVideoStreamInfoEntity(mediumUri: String) = VideoStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    mediumUri = mediumUri,
)

private fun AudioStream.toAudioStreamInfoEntity(mediumUri: String) = AudioStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    sampleFormat = sampleFormat,
    sampleRate = sampleRate,
    channels = channels,
    channelLayout = channelLayout,
    mediumUri = mediumUri,
)

private fun SubtitleStream.toSubtitleStreamInfoEntity(mediumUri: String) = SubtitleStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    mediumUri = mediumUri,
)
