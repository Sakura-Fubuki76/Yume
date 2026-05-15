package com.sakurafubuki.yume.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.sakurafubuki.yume.core.database.dao.DirectoryDao
import com.sakurafubuki.yume.core.database.dao.ImageDimensionDao
import com.sakurafubuki.yume.core.database.dao.MediumDao
import com.sakurafubuki.yume.core.database.dao.WebDavFolderMetadataDao
import com.sakurafubuki.yume.core.database.dao.WebDavDirectoryItemDao
import com.sakurafubuki.yume.core.database.dao.WebDavServerDao
import com.sakurafubuki.yume.core.database.dao.WebDavVideoMetadataDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMediumDao(db: MediaDatabase): MediumDao = db.mediumDao()

    @Provides
    fun provideMediumStateDao(db: MediaDatabase) = db.mediumStateDao()

    @Provides
    fun provideDirectoryDao(db: MediaDatabase): DirectoryDao = db.directoryDao()

    @Provides
    fun provideWebDavServerDao(db: MediaDatabase): WebDavServerDao = db.webDavServerDao()

    @Provides
    fun provideWebDavVideoMetadataDao(db: MediaDatabase): WebDavVideoMetadataDao = db.webDavVideoMetadataDao()

    @Provides
    fun provideWebDavFolderMetadataDao(db: MediaDatabase): WebDavFolderMetadataDao = db.webDavFolderMetadataDao()

    @Provides
    fun provideWebDavDirectoryItemDao(db: MediaDatabase): WebDavDirectoryItemDao = db.webDavDirectoryItemDao()

    @Provides
    fun provideImageDimensionDao(db: MediaDatabase): ImageDimensionDao = db.imageDimensionDao()
}
