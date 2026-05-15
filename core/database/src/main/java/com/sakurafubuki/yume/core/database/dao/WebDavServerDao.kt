package com.sakurafubuki.yume.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sakurafubuki.yume.core.database.entities.WebDavServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavServerDao {

    @Query("SELECT * FROM webdav_servers ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<WebDavServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: WebDavServerEntity): Long

    @Delete
    suspend fun delete(server: WebDavServerEntity)
}
