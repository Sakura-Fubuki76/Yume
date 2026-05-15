package com.sakurafubuki.yume.core.data.mappers

import com.sakurafubuki.yume.core.database.entities.AudioStreamInfoEntity
import com.sakurafubuki.yume.core.model.AudioStreamInfo

fun AudioStreamInfoEntity.toAudioStreamInfo() = AudioStreamInfo(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    sampleFormat = sampleFormat,
    sampleRate = sampleRate,
    channels = channels,
    channelLayout = channelLayout,
)
