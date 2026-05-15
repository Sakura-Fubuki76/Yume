package com.sakurafubuki.yume.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "webdav_directory_items",
    primaryKeys = ["server_id", "parent_path", "href"],
    indices = [
        Index(value = ["server_id", "parent_path"]),
        Index(value = ["server_id", "updated_at"]),
    ],
)
data class WebDavDirectoryItemEntity(
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "parent_path")
    val parentPath: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "href")
    val href: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    @ColumnInfo(name = "size")
    val size: Long,
    @ColumnInfo(name = "width")
    val width: Int?,
    @ColumnInfo(name = "height")
    val height: Int?,
    @ColumnInfo(name = "last_modified")
    val lastModified: Long?,
    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean,
    @ColumnInfo(name = "api_thumbnail_url")
    val apiThumbnailUrl: String?,
    @ColumnInfo(name = "raw_video_url")
    val rawVideoUrl: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
