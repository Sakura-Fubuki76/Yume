package com.sakurafubuki.yume.core.data.mappers

import com.sakurafubuki.yume.core.database.entities.SubtitleStreamInfoEntity
import com.sakurafubuki.yume.core.model.SubtitleStreamInfo

fun SubtitleStreamInfoEntity.toSubtitleStreamInfo() = SubtitleStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
)
