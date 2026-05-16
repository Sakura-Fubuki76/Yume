package com.sakurafubuki.yume.feature.player.state

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun rememberNetworkSeekTrafficInfo(
    player: Player,
    active: Boolean,
): String? {
    var mediaId by remember(player) { mutableStateOf(player.currentMediaItem?.mediaId.orEmpty()) }
    var snapshot by remember(player) { mutableStateOf<NetworkTrafficSnapshot?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaId = mediaItem?.mediaId.orEmpty()
            }
        }
        player.addListener(listener)
        mediaId = player.currentMediaItem?.mediaId.orEmpty()
        onDispose {
            player.removeListener(listener)
        }
    }

    val isNetworkMedia = remember(mediaId) { mediaId.isHttpMediaId() }
    LaunchedEffect(active, isNetworkMedia, mediaId) {
        if (!active || !isNetworkMedia) {
            snapshot = null
            return@LaunchedEffect
        }

        val uid = Process.myUid()
        val startBytes = readUidRxBytes(uid) ?: run {
            snapshot = null
            return@LaunchedEffect
        }

        var lastBytes = startBytes
        var lastTimeMs = SystemClock.elapsedRealtime()
        snapshot = NetworkTrafficSnapshot(bytesPerSecond = 0L, bytesSinceStart = 0L)

        while (isActive) {
            delay(500L)
            val nowBytes = readUidRxBytes(uid) ?: break
            val nowTimeMs = SystemClock.elapsedRealtime()
            val byteDelta = (nowBytes - lastBytes).coerceAtLeast(0L)
            val timeDelta = (nowTimeMs - lastTimeMs).coerceAtLeast(1L)
            snapshot = NetworkTrafficSnapshot(
                bytesPerSecond = byteDelta * 1000L / timeDelta,
                bytesSinceStart = (nowBytes - startBytes).coerceAtLeast(0L),
            )
            lastBytes = nowBytes
            lastTimeMs = nowTimeMs
        }
    }

    return snapshot?.let { "Net ${formatBytes(it.bytesPerSecond)}/s | +${formatBytes(it.bytesSinceStart)}" }
}

private data class NetworkTrafficSnapshot(
    val bytesPerSecond: Long,
    val bytesSinceStart: Long,
)

private fun readUidRxBytes(uid: Int): Long? {
    val bytes = TrafficStats.getUidRxBytes(uid)
    return bytes.takeIf { it != TrafficStats.UNSUPPORTED.toLong() }
}

private fun String.isHttpMediaId(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
    }
}
