package com.sakurafubuki.yume.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.sakurafubuki.yume.core.data.repository.CloudVideoMetadataRepository
import com.sakurafubuki.yume.core.data.repository.LocalCloudVideoMetadataRepository
import com.sakurafubuki.yume.core.data.repository.LocalMediaRepository
import com.sakurafubuki.yume.core.data.repository.LocalPreferencesRepository
import com.sakurafubuki.yume.core.data.repository.LocalSearchHistoryRepository
import com.sakurafubuki.yume.core.data.repository.LocalWebDavServerRepository
import com.sakurafubuki.yume.core.data.repository.MediaRepository
import com.sakurafubuki.yume.core.data.repository.PreferencesRepository
import com.sakurafubuki.yume.core.data.repository.SearchHistoryRepository
import com.sakurafubuki.yume.core.data.repository.WebDavServerRepository
import com.sakurafubuki.yume.core.data.webdav.SardineFactory
import com.sakurafubuki.yume.core.data.webdav.SardineFactoryImpl
import com.sakurafubuki.yume.core.data.webdav.WebDavRepository
import com.sakurafubuki.yume.core.data.webdav.WebDavRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindsSearchHistoryRepository(
        searchHistoryRepository: LocalSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    fun bindsWebDavServerRepository(
        repository: LocalWebDavServerRepository,
    ): WebDavServerRepository

    @Binds
    @Singleton
    fun bindsWebDavRepository(
        repository: WebDavRepositoryImpl,
    ): WebDavRepository

    @Binds
    @Singleton
    fun bindsCloudVideoMetadataRepository(
        repository: LocalCloudVideoMetadataRepository,
    ): CloudVideoMetadataRepository

    @Binds
    @Singleton
    fun bindsSardineFactory(
        sardineFactoryImpl: SardineFactoryImpl,
    ): SardineFactory
}
