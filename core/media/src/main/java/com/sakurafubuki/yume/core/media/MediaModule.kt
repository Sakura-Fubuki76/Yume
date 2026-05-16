package com.sakurafubuki.yume.core.media

import com.sakurafubuki.yume.core.media.services.LocalMediaService
import com.sakurafubuki.yume.core.media.services.MediaService
import com.sakurafubuki.yume.core.media.sync.LocalMediaInfoSynchronizer
import com.sakurafubuki.yume.core.media.sync.LocalMediaSynchronizer
import com.sakurafubuki.yume.core.media.sync.MediaInfoSynchronizer
import com.sakurafubuki.yume.core.media.sync.MediaSynchronizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    @Singleton
    fun bindsMediaSynchronizer(
        mediaSynchronizer: LocalMediaSynchronizer,
    ): MediaSynchronizer

    @Binds
    @Singleton
    fun bindsMediaInfoSynchronizer(
        mediaInfoSynchronizer: LocalMediaInfoSynchronizer,
    ): MediaInfoSynchronizer

    @Binds
    @Singleton
    fun bindMediaService(
        mediaService: LocalMediaService,
    ): MediaService
}
