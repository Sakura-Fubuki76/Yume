package com.sakurafubuki.yume.core.data.mappers

import com.sakurafubuki.yume.core.database.entities.VideoStreamInfoEntity
import com.sakurafubuki.yume.core.model.VideoStreamInfo

fun VideoStreamInfoEntity.toVideoStreamInfo() = VideoStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
)
