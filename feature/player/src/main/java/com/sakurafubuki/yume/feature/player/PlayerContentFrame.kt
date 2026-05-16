package com.sakurafubuki.yume.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.sakurafubuki.yume.core.common.Logger
import com.sakurafubuki.yume.core.model.VideoContentScale
import com.sakurafubuki.yume.feature.player.ass.AssSubtitleState
import com.sakurafubuki.yume.feature.player.ass.AssSubtitleView
import com.sakurafubuki.yume.feature.player.ass.rememberAssSubtitleState
import com.sakurafubuki.yume.feature.player.extensions.toContentScale
import com.sakurafubuki.yume.feature.player.state.ControlsVisibilityState
import com.sakurafubuki.yume.feature.player.state.PictureInPictureState
import com.sakurafubuki.yume.feature.player.state.SeekGestureState
import com.sakurafubuki.yume.feature.player.state.TapGestureState
import com.sakurafubuki.yume.feature.player.state.VideoZoomAndContentScaleState
import com.sakurafubuki.yume.feature.player.state.VolumeAndBrightnessGestureState
import com.sakurafubuki.yume.feature.player.ui.PlayerGestures
import com.sakurafubuki.yume.feature.player.ui.ShutterView
import com.sakurafubuki.yume.feature.player.ui.SubtitleConfiguration
import com.sakurafubuki.yume.feature.player.ui.SubtitleView
import kotlin.math.roundToInt

private data class LetterboxedRect(
    val width: Int,
    val height: Int,
    val offsetX: Float,
    val offsetY: Float,
)

private fun computeLetterboxedRect(
    containerW: Int,
    containerH: Int,
    videoW: Int,
    videoH: Int,
    contentScale: VideoContentScale,
): LetterboxedRect {
    if (containerW <= 0 || containerH <= 0 || videoW <= 0 || videoH <= 0) {
        return LetterboxedRect(0, 0, 0f, 0f)
    }
    return when (contentScale) {
        VideoContentScale.STRETCH ->
            LetterboxedRect(containerW, containerH, 0f, 0f)

        VideoContentScale.HUNDRED_PERCENT -> {
            val ox = ((containerW - videoW) / 2f).coerceAtLeast(0f)
            val oy = ((containerH - videoH) / 2f).coerceAtLeast(0f)
            LetterboxedRect(videoW, videoH, ox, oy)
        }

        VideoContentScale.BEST_FIT, VideoContentScale.CROP -> {
            val videoAspect = videoW.toFloat() / videoH
            val containerAspect = containerW.toFloat() / containerH

            val (displayW, displayH) = if (contentScale == VideoContentScale.BEST_FIT) {
                if (videoAspect > containerAspect) {
                    containerW to (containerW / videoAspect).roundToInt()
                } else {
                    (containerH * videoAspect).roundToInt() to containerH
                }
            } else {
                if (videoAspect > containerAspect) {
                    (containerH * videoAspect).roundToInt() to containerH
                } else {
                    containerW to (containerW / videoAspect).roundToInt()
                }
            }
            val ox = ((containerW - displayW) / 2f).coerceAtLeast(0f)
            val oy = ((containerH - displayH) / 2f).coerceAtLeast(0f)
            LetterboxedRect(displayW, displayH, ox, oy)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    assState: AssSubtitleState,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
) {
    val presentationState = rememberPresentationState(player)

    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    var surfaceWidthPx by remember { mutableIntStateOf(0) }
    var surfaceHeightPx by remember { mutableIntStateOf(0) }
    var surfaceOffsetXPx by remember { mutableFloatStateOf(0f) }
    var surfaceOffsetYPx by remember { mutableFloatStateOf(0f) }

    var videoStorageW by remember { mutableIntStateOf(0) }
    var videoStorageH by remember { mutableIntStateOf(0) }

    val displayRect = computeLetterboxedRect(
        containerW = containerWidthPx,
        containerH = containerHeightPx,
        videoW = videoStorageW,
        videoH = videoStorageH,
        contentScale = videoZoomAndContentScaleState.videoContentScale,
    )

    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = modifier
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                pictureInPictureState.setVideoViewRect(
                    Rect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    ),
                )
                containerWidthPx = coords.size.width
                containerHeightPx = coords.size.height
            }
            .resizeWithContentScale(
                contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                sourceSizeDp = presentationState.videoSizeDp?.let { size ->
                    size.copy(
                        width = with(LocalDensity.current) { size.width.toDp().value },
                        height = with(LocalDensity.current) { size.height.toDp().value },
                    )
                },
            )
            .onGloballyPositioned { coords ->
                surfaceWidthPx = coords.size.width
                surfaceHeightPx = coords.size.height
                val pos = coords.positionInParent()
                surfaceOffsetXPx = pos.x
                surfaceOffsetYPx = pos.y
            }
            .graphicsLayer {
                scaleX = videoZoomAndContentScaleState.zoom
                scaleY = videoZoomAndContentScaleState.zoom
                translationX = videoZoomAndContentScaleState.offset.x
                translationY = videoZoomAndContentScaleState.offset.y
            },
    )
    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        pictureInPictureState = pictureInPictureState,
        seekGestureState = seekGestureState,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
    )

    val assSubtitleInfo = player.currentTracks.groups.firstOrNull { group ->
        group.type == androidx.media3.common.C.TRACK_TYPE_TEXT && group.isSelected
    }?.let { group ->
        val format = group.getTrackFormat(0)
        val rawId = format.id ?: ""
        val mimeType = format.sampleMimeType
        val isAss = mimeType != null && (mimeType == androidx.media3.common.MimeTypes.TEXT_SSA)
        val realId = rawId.substringAfter(":")
        val uri = try {
            val candidate = realId.toUri()
            if (candidate.scheme != null) candidate else null
        } catch (_: Exception) {
            null
        }
        val hasAssExt = uri?.path?.let { it.endsWith(".ass") || it.endsWith(".ssa") } == true ||
            realId.endsWith(".ass", ignoreCase = true) ||
            realId.endsWith(".ssa", ignoreCase = true)
        Triple(uri, isAss || hasAssExt, rawId)
    }

    val isAssSubtitle = assSubtitleInfo?.second == true
    val currentSubtitleUri = assSubtitleInfo?.first

    Logger.d(
        "PlayerContentFrame",
        "ASS detection: isAssSubtitle=$isAssSubtitle, " +
            "trackId=${assSubtitleInfo?.third}, " +
            "mimeType=${player.currentTracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected }?.getTrackFormat(0)?.sampleMimeType}, " +
            "uri=${assSubtitleInfo?.first}",
    )

    if (!isAssSubtitle) {
        SubtitleView(
            player = player,
            isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
            configuration = subtitleConfiguration,
        )
    }

    fun videoSizeFromTracks(player: Player): VideoSize {
        for (group in player.currentTracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected) {
                val fmt = group.getTrackFormat(0)
                Logger.d(
                    "PlayerContentFrame",
                    "videoFormat: ${fmt.width}x${fmt.height}, " +
                        "codecs=${fmt.codecs}, sampleMime=${fmt.sampleMimeType}",
                )
                if (fmt.width > 0 && fmt.height > 0) {
                    return VideoSize(fmt.width, fmt.height, fmt.rotationDegrees, fmt.pixelWidthHeightRatio)
                }
            }
        }
        Logger.d("PlayerContentFrame", "videoSizeFromTracks: no selected video track found")
        return VideoSize.UNKNOWN
    }

    val videoPixelSize by produceState(VideoSize.UNKNOWN) {
        val initial = player.videoSize
        value = if (initial.width > 0 && initial.height > 0) {
            initial
        } else {
            videoSizeFromTracks(player)
        }

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                value = videoSize
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val trackSize = videoSizeFromTracks(player)
                if (trackSize.width > 0 && trackSize.height > 0) value = trackSize
            }
        }
        player.addListener(listener)
        awaitDispose { player.removeListener(listener) }
    }
    LaunchedEffect(videoPixelSize) {
        videoStorageW = videoPixelSize.width
        videoStorageH = videoPixelSize.height
    }

    rememberAssSubtitleState(
        state = assState,
        player = player,
        assFileUri = if (isAssSubtitle) currentSubtitleUri else null,
        configuration = subtitleConfiguration,
    )

    LaunchedEffect(
        displayRect.width,
        displayRect.height,
        surfaceWidthPx,
        surfaceHeightPx,
    ) {
        if (displayRect.width > 0 && displayRect.height > 0) {
            Logger.d(
                "PlayerContentFrame",
                "setFrameSize from letterboxed rect: ${displayRect.width}x${displayRect.height} " +
                    "(container=${containerWidthPx}x$containerHeightPx, " +
                    "video=${videoStorageW}x$videoStorageH)",
            )
            assState.setFrameSize(displayRect.width, displayRect.height)
        } else if (surfaceWidthPx > 0 && surfaceHeightPx > 0) {
            Logger.d(
                "PlayerContentFrame",
                "setFrameSize from surface fallback: ${surfaceWidthPx}x$surfaceHeightPx " +
                    "(container=${containerWidthPx}x$containerHeightPx, " +
                    "video=${videoStorageW}x$videoStorageH)",
            )
            assState.setFrameSize(surfaceWidthPx, surfaceHeightPx)
        }
    }

    val hasDisplayArea = (displayRect.width > 0 && displayRect.height > 0) ||
        (surfaceWidthPx > 0 && surfaceHeightPx > 0)
    if (isAssSubtitle && hasDisplayArea) {
        val useRect = displayRect.width > 0 && displayRect.height > 0
        val w = if (useRect) displayRect.width else surfaceWidthPx
        val h = if (useRect) displayRect.height else surfaceHeightPx

        val ox = if (useRect) {
            displayRect.offsetX.toInt()
        } else {
            surfaceOffsetXPx.toInt()
        }
        val oy = if (useRect) {
            displayRect.offsetY.toInt()
        } else {
            surfaceOffsetYPx.toInt()
        }
        val density = LocalDensity.current
        val widthDp = with(density) { w.toDp() }
        val heightDp = with(density) { h.toDp() }
        AssSubtitleView(
            state = assState,
            modifier = Modifier
                .offset { IntOffset(ox, oy) }
                .size(widthDp, heightDp),
        )
    }

    if (presentationState.coverSurface) {
        ShutterView()
    }
}
