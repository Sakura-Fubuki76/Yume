package com.sakurafubuki.yume.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sakurafubuki.yume.core.database.entities.ImageDimensionEntity

@Dao
interface ImageDimensionDao {

    @Query("SELECT * FROM image_dimensions WHERE server_id = :serverId AND uri = :uri")
    suspend fun get(serverId: Int, uri: String): ImageDimensionEntity?

    @Query("SELECT * FROM image_dimensions WHERE server_id = :serverId AND uri IN (:uris)")
    suspend fun getByServerIdAndUris(serverId: Int, uris: List<String>): List<ImageDimensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageDimensionEntity)

    @Query("SELECT COUNT(*) FROM image_dimensions WHERE server_id = :serverId")
    suspend fun count(serverId: Int): Int

    @Query(
        """
        DELETE FROM image_dimensions
        WHERE (server_id, uri) IN (
            SELECT server_id, uri FROM image_dimensions
            WHERE server_id = :serverId
            ORDER BY last_accessed_at ASC
            LIMIT :excess
        )
        """,
    )
    suspend fun deleteOldestEntries(serverId: Int, excess: Int)

    @Transaction
    suspend fun upsertWithEviction(entity: ImageDimensionEntity, maxEntriesPerServer: Int) {
        upsert(entity)
        val excess = count(entity.serverId) - maxEntriesPerServer
        if (excess > 0) {
            deleteOldestEntries(entity.serverId, excess)
        }
    }
}
