package com.sakurafubuki.yume.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "image_dimensions",
    primaryKeys = ["server_id", "uri"],
    indices = [
        Index(value = ["server_id"]),
    ],
)
data class ImageDimensionEntity(
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "uri")
    val uri: String,
    @ColumnInfo(name = "width")
    val width: Int,
    @ColumnInfo(name = "height")
    val height: Int,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,
)
