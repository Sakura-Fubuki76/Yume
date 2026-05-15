package com.sakurafubuki.yume.core.data.mappers

import com.sakurafubuki.yume.core.common.Utils
import com.sakurafubuki.yume.core.database.relations.DirectoryWithMedia
import com.sakurafubuki.yume.core.database.relations.MediumWithInfo
import com.sakurafubuki.yume.core.model.Folder

fun DirectoryWithMedia.toFolder() = Folder(
    name = directory.name,
    path = directory.path,
    dateModified = directory.modified,
    parentPath = directory.parentPath,
    formattedMediaSize = Utils.formatFileSize(media.sumOf { it.mediumEntity.size }),
    mediaList = media.map(MediumWithInfo::toVideo),
    mediaCount = media.size,
)
