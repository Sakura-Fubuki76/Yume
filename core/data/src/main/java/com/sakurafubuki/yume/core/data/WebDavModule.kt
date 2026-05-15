package com.sakurafubuki.yume.core.data

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebDavModule {

    @Provides
    @Singleton
    fun provideSardine(): Sardine = OkHttpSardine()
}
