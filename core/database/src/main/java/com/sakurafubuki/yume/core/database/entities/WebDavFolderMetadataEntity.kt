package com.sakurafubuki.yume.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "webdav_folder_metadata",
    primaryKeys = ["server_id", "folder_path"],
    indices = [
        Index(value = ["server_id"]),
    ],
)
data class WebDavFolderMetadataEntity(
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "folder_path")
    val folderPath: String,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long,
    @ColumnInfo(name = "total_size")
    val totalSize: Long,
    @ColumnInfo(name = "media_count")
    val mediaCount: Int,
    @ColumnInfo(name = "folder_count")
    val folderCount: Int,
    @ColumnInfo(name = "cover_image_uri")
    val coverImageUri: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "video_count")
    val videoCount: Int = 0,
    @ColumnInfo(name = "image_count")
    val imageCount: Int = 0,
)
