package com.sakurafubuki.yume.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sakurafubuki.yume.core.database.entities.WebDavVideoMetadataEntity

@Dao
interface WebDavVideoMetadataDao {

    @Query("SELECT * FROM webdav_video_metadata WHERE server_id = :serverId AND href IN (:hrefs)")
    suspend fun getByServerAndHrefs(serverId: Int, hrefs: List<String>): List<WebDavVideoMetadataEntity>

    @Query("SELECT * FROM webdav_video_metadata WHERE server_id = :serverId")
    fun observeByServer(serverId: Int): kotlinx.coroutines.flow.Flow<List<WebDavVideoMetadataEntity>>

    @Query("SELECT * FROM webdav_video_metadata WHERE server_id = :serverId AND href IN (:hrefs)")
    fun observeByServerAndHrefs(
        serverId: Int,
        hrefs: List<String>,
    ): kotlinx.coroutines.flow.Flow<List<WebDavVideoMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WebDavVideoMetadataEntity>)

    @Query("UPDATE webdav_video_metadata SET thumbnail_path = NULL")
    suspend fun clearAllThumbnailPaths()

    @Query("DELETE FROM webdav_video_metadata WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Int)
}
