package com.sakurafubuki.yume

import coil3.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        appImageLoaderFactory: AppImageLoaderFactory,
    ): ImageLoader = appImageLoaderFactory.create()
}
