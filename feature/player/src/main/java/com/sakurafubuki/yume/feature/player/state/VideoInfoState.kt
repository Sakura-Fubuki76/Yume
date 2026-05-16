package com.sakurafubuki.yume.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

@Stable
class VideoInfoState(private val player: Player) {

    var title: String? by mutableStateOf(null)
        private set
    var mimeType: String? by mutableStateOf(null)
        private set
    var codecs: String? by mutableStateOf(null)
        private set
    var bitrate: Int by mutableStateOf(Format.NO_VALUE)
        private set
    var frameRate: Float by mutableStateOf(0f)
        private set
    var width: Int by mutableStateOf(0)
        private set
    var height: Int by mutableStateOf(0)
        private set
    var colorSpace: Int by mutableStateOf(Format.NO_VALUE)
        private set
    var colorTransfer: Int by mutableStateOf(Format.NO_VALUE)
        private set
    var colorRange: Int by mutableStateOf(Format.NO_VALUE)
        private set

    val isReady: Boolean get() = width > 0 || codecs != null
    val resolutionFormatted: String get() = if (width > 0 && height > 0) "$width×$height" else ""
    val bitrateFormatted: String?
        get() {
            if (bitrate == Format.NO_VALUE || bitrate <= 0) return null
            return when {
                bitrate >= 1_000_000 -> "%.1f Mbps".format(bitrate / 1_000_000f)
                bitrate >= 1_000 -> "%.0f kbps".format(bitrate / 1_000f)
                else -> "$bitrate bps"
            }
        }
    val frameRateFormatted: String?
        get() {
            if (frameRate <= 0f) return null
            return "%.3f fps".format(frameRate)
        }
    val codecName: String? get() = formatCodecName(codecs, mimeType)

    val containerFormat: String?
        get() {
            return when {
                mimeType == "video/mp4" || mimeType == "video/mp4v-es" -> "MP4"
                mimeType == "video/x-matroska" -> "Matroska (MKV)"
                mimeType == "video/webm" -> "WebM"
                mimeType == "video/quicktime" -> "QuickTime (MOV)"
                mimeType == "video/avi" -> "AVI"
                mimeType == "video/x-msvideo" -> "AVI"
                else -> mimeType?.substringAfter("/")?.uppercase()
            }
        }

    val colorSpaceFormatted: String?
        get() = when (colorSpace) {
            C.COLOR_SPACE_BT601 -> "BT.601"
            C.COLOR_SPACE_BT709 -> "BT.709"
            C.COLOR_SPACE_BT2020 -> "BT.2020"
            else -> null
        }
    val colorTransferFormatted: String?
        get() = when (colorTransfer) {
            C.COLOR_TRANSFER_SDR -> "SDR"
            C.COLOR_TRANSFER_ST2084 -> "PQ (ST.2084)"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
    val dynamicRangeFormatted: String?
        get() {
            val transfer = colorTransferFormatted ?: return null
            val space = colorSpaceFormatted
            return if (space != null) "$transfer / $space" else transfer
        }
    val colorRangeFormatted: String?
        get() = when (colorRange) {
            C.COLOR_RANGE_LIMITED -> "Limited"
            C.COLOR_RANGE_FULL -> "Full"
            else -> null
        }

    @OptIn(UnstableApi::class)
    suspend fun observe() {
        title = player.mediaMetadata.title?.toString()
        refreshTracks(player.currentTracks)
        refreshVideoSize(player.videoSize)

        player.listen { events ->
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                title = player.mediaMetadata.title?.toString()
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                refreshTracks(player.currentTracks)
            }
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                refreshVideoSize(player.videoSize)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun refreshTracks(tracks: Tracks) {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || !group.isTrackSupported(0)) continue
            val format = group.getTrackFormat(0)
            mimeType = format.sampleMimeType
            codecs = format.codecs
            bitrate = format.bitrate
            frameRate = format.frameRate
            if (format.width != Format.NO_VALUE) width = format.width
            if (format.height != Format.NO_VALUE) height = format.height
            format.colorInfo?.let { ci ->
                colorSpace = ci.colorSpace
                colorTransfer = ci.colorTransfer
                colorRange = ci.colorRange
            }
            break
        }
    }

    private fun refreshVideoSize(size: androidx.media3.common.VideoSize) {
        val w = size.width
        val h = size.height
        if (w > 0 && h > 0) {
            width = w
            height = h
        }
    }
}

@Composable
fun rememberVideoInfoState(player: Player): VideoInfoState {
    val state = remember { VideoInfoState(player) }
    LaunchedEffect(player) { state.observe() }
    return state
}

fun formatCodecName(codecs: String?, mimeType: String?): String? {
    if (codecs.isNullOrEmpty() && mimeType.isNullOrEmpty()) return null

    if (!codecs.isNullOrEmpty()) {
        val parts = codecs.split(".")
        val prefix = parts.firstOrNull()?.lowercase() ?: ""

        when {
            prefix.startsWith("avc") -> {
                val shortName = "H.264"
                if (parts.size >= 3) {
                    val profileHex = parts[1].lowercase()
                    val levelHex = parts[2].lowercase()
                    val profileName = avcProfileName(profileHex)
                    val level = avcLevel(levelHex)
                    return if (level != null) "$shortName $profileName@L$level" else "$shortName $profileName"
                }
                return shortName
            }
            prefix.startsWith("hvc") || prefix.startsWith("hev") -> {
                val shortName = "HEVC"
                if (parts.size >= 3) {
                    val profileIdc = parts[1].toIntOrNull() ?: return shortName
                    val profileName = hevcProfileName(profileIdc)
                    val levelHex = parts.getOrNull(2)?.lowercase()?.removePrefix("l") ?: ""
                    val level = levelHex.toIntOrNull()?.let { it / 3 }
                    return if (level != null) "$shortName $profileName@L$level.${levelHex.toIntOrNull()?.let { it % 3 } ?: 0}" else "$shortName $profileName"
                }
                return shortName
            }
            prefix.startsWith("vp9") -> {
                val shortName = "VP9"
                if (parts.size >= 2) {
                    val profileName = vp9ProfileName(parts[1])
                    return "$shortName $profileName"
                }
                return shortName
            }
            prefix.startsWith("av01") || prefix.startsWith("av1.") -> return "AV1"
            prefix.startsWith("vp8") -> return "VP8"
            prefix.startsWith("mp4v") -> return "MPEG-4 Visual"
            else -> return codecs.uppercase()
        }
    }

    return when (mimeType?.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/x-vnd.on2.vp9", "video/vp9" -> "VP9"
        "video/x-vnd.on2.vp8", "video/vp8" -> "VP8"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4 Visual"
        "video/3gpp" -> "H.263"
        else -> mimeType?.substringAfter("/")?.uppercase()
    }
}

private fun avcProfileName(hex: String): String = when (hex) {
    "42" -> "Baseline"
    "4d" -> "Main"
    "58" -> "Extended"
    "64" -> "High"
    "6e" -> "High 10"
    "7a" -> "High 4:2:2"
    "f4" -> "High 4:4:4 Predictive"
    else -> "Profile 0x$hex"
}

private fun avcLevel(hex: String): String? {
    val val_ = hex.toIntOrNull(16) ?: return null
    return "${val_ / 10}.${val_ % 10}"
}

private fun hevcProfileName(idc: Int): String = when (idc) {
    1 -> "Main"
    2 -> "Main 10"
    3 -> "Main Still Picture"
    else -> "Profile $idc"
}

private fun vp9ProfileName(profile: String): String = when (profile) {
    "0" -> ""
    "1" -> "Profile 1"
    "2" -> "Profile 2"
    "3" -> "Profile 3"
    else -> profile
}
