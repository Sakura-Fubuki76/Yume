package com.sakurafubuki.yume.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "webdav_video_metadata",
    primaryKeys = ["server_id", "href"],
    indices = [
        Index(value = ["server_id"]),
    ],
)
data class WebDavVideoMetadataEntity(
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "href")
    val href: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "width")
    val width: Int = 0,
    @ColumnInfo(name = "height")
    val height: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
