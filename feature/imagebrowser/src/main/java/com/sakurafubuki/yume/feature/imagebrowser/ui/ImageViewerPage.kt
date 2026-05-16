package com.sakurafubuki.yume.feature.imagebrowser.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sakurafubuki.yume.core.model.ImageQuality
import kotlin.math.abs
import kotlin.math.max

private const val IMAGE_VIEWER_MIN_SCALE = 1f
private const val IMAGE_VIEWER_MAX_SCALE = 8f
private const val IMAGE_VIEWER_DEFAULT_DOUBLE_TAP_SCALE = 2.5f
private const val IMAGE_VIEWER_SCALE_EPSILON = 0.01f

@Composable
internal fun ImageViewerPage(
    imageUri: String,
    localImageLoader: ImageLoader,
    imageQuality: ImageQuality,
    enableSwipeToDismiss: Boolean,
    swipeDismissHeightPx: Float,
    swipeDismissWidthPx: Float,
    onScaleChanged: (Float) -> Unit,
    onMultiTouchChanged: (Boolean) -> Unit = {},
    onSwipeDismissProgress: (Float) -> Unit,
    onSwipeDismissRelease: (Float, Float, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pageState = remember(imageUri) { ImageViewerPageState() }
    val updatedOnScaleChanged by rememberUpdatedState(onScaleChanged)
    val updatedOnMultiTouchChanged by rememberUpdatedState(onMultiTouchChanged)
    val updatedOnSwipeDismissProgress by rememberUpdatedState(onSwipeDismissProgress)
    val updatedOnSwipeDismissRelease by rememberUpdatedState(onSwipeDismissRelease)

    val gridQuality = ImageViewerStore.previewQuality
    val gridDisplayUri = remember(imageUri) { ImageViewerStore.displayUriFor(imageUri) }
    val thumbnailMaxEdgePx = ImageViewerStore.imageBrowserThumbnailSizePx
    val placeholderKey = remember(gridDisplayUri, gridQuality, thumbnailMaxEdgePx) {
        thumbnailMemoryCacheKey(gridDisplayUri, gridQuality, thumbnailMaxEdgePx)
    }
    var viewerLoaded by remember(imageUri) { mutableStateOf(false) }
    val viewerAlpha by animateFloatAsState(
        targetValue = if (viewerLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "viewer-image-alpha",
    )
    val thumbnailRequest: ImageRequest = remember(gridDisplayUri, gridQuality, thumbnailMaxEdgePx) {
        buildImageRequest(
            context = context,
            data = gridDisplayUri,
            quality = gridQuality,
            profile = ImageRequestProfile.THUMBNAIL,
            thumbnailMaxEdgePx = thumbnailMaxEdgePx,
        )
    }
    val viewerRequest: ImageRequest = remember(imageUri, imageQuality, placeholderKey, thumbnailMaxEdgePx) {
        buildImageRequest(
            context = context,
            data = imageUri,
            quality = imageQuality,
            profile = ImageRequestProfile.VIEWER,
            thumbnailMaxEdgePx = thumbnailMaxEdgePx,
        )
            .newBuilder()
            .placeholderMemoryCacheKey(placeholderKey)
            .build()
    }

    LaunchedEffect(pageState.scale) {
        updatedOnScaleChanged(pageState.scale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged(pageState::updateViewportSize)
            .graphicsLayer {
                val dismissProgress = pageState.dismissProgress(swipeDismissWidthPx, swipeDismissHeightPx)
                val dismissScale = 1f - (0.08f * dismissProgress)
                scaleX = pageState.scale * dismissScale
                scaleY = pageState.scale * dismissScale
                translationX = pageState.offset.x + pageState.dismissOffsetX
                translationY = pageState.offset.y + pageState.dismissOffsetY
            }
            .pointerInput(pageState.isZoomed) {
                if (!pageState.isZoomed) return@pointerInput
                detectImageViewerTransformGestures(
                    pointCount = 1,
                    onGesture = { _, pan, _, _ ->
                        pageState.panBy(pan)
                    },
                )
            }
            .pointerInput(Unit) {
                detectImageViewerTransformGestures(
                    pointCount = 2,
                    onGestureStart = {
                        pageState.updateMultiTouchActive(true)
                        updatedOnMultiTouchChanged(true)
                    },
                    onGesture = { centroid, pan, zoom, _ ->
                        pageState.applyTransform(
                            centroid = centroid,
                            pan = pan,
                            zoomChange = zoom,
                        )
                    },
                    onGestureEnd = {
                        pageState.updateMultiTouchActive(false)
                        updatedOnMultiTouchChanged(false)
                    },
                )
            }
            .pointerInput(enableSwipeToDismiss, pageState.isZoomed, swipeDismissHeightPx) {
                if (!enableSwipeToDismiss || pageState.isZoomed) return@pointerInput
                var releaseVelocityY = 0f
                var lastDragEventTimeMs: Long? = null
                detectImageViewerVerticalDragGestures(
                    onDragStart = {
                        lastDragEventTimeMs = SystemClock.uptimeMillis()
                        releaseVelocityY = 0f
                    },
                    onDragCancel = {
                        val rect = pageState.currentImageRect(swipeDismissWidthPx, swipeDismissHeightPx)
                        updatedOnSwipeDismissRelease(0f, 0f, rect)
                        pageState.resetDismiss()
                        lastDragEventTimeMs = null
                        releaseVelocityY = 0f
                    },
                    onDragEnd = {
                        val dismissProgress = pageState.dismissProgress(swipeDismissWidthPx, swipeDismissHeightPx)
                        val rect = pageState.currentImageRect(swipeDismissWidthPx, swipeDismissHeightPx)
                        updatedOnSwipeDismissRelease(dismissProgress, releaseVelocityY, rect)
                        pageState.resetDismiss()
                        lastDragEventTimeMs = null
                        releaseVelocityY = 0f
                    },
                ) { change, dragAmount ->
                    val now = SystemClock.uptimeMillis()
                    lastDragEventTimeMs?.let { previous ->
                        val deltaMs = (now - previous).coerceAtLeast(1L)
                        val instantVelocityY = dragAmount / deltaMs.toFloat() * 1000f
                        releaseVelocityY = if (instantVelocityY > 0f) instantVelocityY else 0f
                    }
                    lastDragEventTimeMs = now
                    pageState.updateVerticalDismissOffset(dragAmount)
                    updatedOnSwipeDismissProgress(pageState.dismissProgress(swipeDismissWidthPx, swipeDismissHeightPx))
                    change.consume()
                }
            }
            .pointerInput(enableSwipeToDismiss, pageState.isZoomed, swipeDismissWidthPx, density) {
                if (!enableSwipeToDismiss || pageState.isZoomed) return@pointerInput
                val edgeWidthPx = with(density) { 18.dp.toPx() }
                var releaseVelocityX = 0f
                var lastDragEventTimeMs: Long? = null
                detectImageViewerHorizontalEdgeDragGestures(
                    viewportWidthPx = swipeDismissWidthPx,
                    edgeWidthPx = edgeWidthPx,
                    onDragStart = {
                        lastDragEventTimeMs = SystemClock.uptimeMillis()
                        releaseVelocityX = 0f
                    },
                    onDragCancel = {
                        val rect = pageState.currentImageRect(swipeDismissWidthPx, swipeDismissHeightPx)
                        updatedOnSwipeDismissRelease(0f, 0f, rect)
                        pageState.resetDismiss()
                        lastDragEventTimeMs = null
                        releaseVelocityX = 0f
                    },
                    onDragEnd = {
                        val dismissProgress = pageState.dismissProgress(swipeDismissWidthPx, swipeDismissHeightPx)
                        val rect = pageState.currentImageRect(swipeDismissWidthPx, swipeDismissHeightPx)
                        updatedOnSwipeDismissRelease(dismissProgress, releaseVelocityX, rect)
                        pageState.resetDismiss()
                        lastDragEventTimeMs = null
                        releaseVelocityX = 0f
                    },
                ) { change, dragAmount ->
                    val now = SystemClock.uptimeMillis()
                    lastDragEventTimeMs?.let { previous ->
                        val deltaMs = (now - previous).coerceAtLeast(1L)
                        releaseVelocityX = abs(dragAmount / deltaMs.toFloat() * 1000f)
                    }
                    lastDragEventTimeMs = now
                    pageState.updateHorizontalDismissOffset(dragAmount)
                    updatedOnSwipeDismissProgress(pageState.dismissProgress(swipeDismissWidthPx, swipeDismissHeightPx))
                    change.consume()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        pageState.toggleDoubleTapZoom(tapOffset)
                    },
                )
            },
    ) {
        AsyncImage(
            model = thumbnailRequest,
            imageLoader = resolveImageLoader(context, gridDisplayUri, localImageLoader),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                if (!viewerLoaded) {
                    pageState.updateImageSize(state.result.image.width, state.result.image.height)
                }
            },
        )
        AsyncImage(
            model = viewerRequest,
            imageLoader = resolveImageLoader(context, imageUri, localImageLoader),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = viewerAlpha },
            onLoading = { viewerLoaded = false },
            onError = { viewerLoaded = false },
            onSuccess = { state ->
                viewerLoaded = true
                pageState.updateImageSize(state.result.image.width, state.result.image.height)
            },
        )
    }
}

@Stable
internal class ImageViewerPageState(
    private val minScale: Float = IMAGE_VIEWER_MIN_SCALE,
    private val maxScale: Float = IMAGE_VIEWER_MAX_SCALE,
) {
    var scale by mutableFloatStateOf(minScale)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    var dismissOffsetY by mutableFloatStateOf(0f)
        private set

    var dismissOffsetX by mutableFloatStateOf(0f)
        private set

    var isZoomed by mutableStateOf(false)
        private set

    var isMultiTouchActive by mutableStateOf(false)
        private set

    fun updateMultiTouchActive(active: Boolean) {
        isMultiTouchActive = active
    }

    private var viewportSize by mutableStateOf(IntSize.Zero)
    private var imageSize by mutableStateOf(IntSize.Zero)

    fun updateViewportSize(size: IntSize) {
        viewportSize = size
        offset = clampOffset(offset, scale)
    }

    fun updateImageSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        imageSize = IntSize(width, height)
        offset = clampOffset(offset, scale)
    }

    fun applyTransform(
        centroid: Offset,
        pan: Offset,
        zoomChange: Float,
    ) {
        dismissOffsetY = 0f
        val previousScale = scale
        val nextScale = normalizeScale(previousScale * zoomChange)
        if (nextScale <= minScale) {
            scale = minScale
            offset = Offset.Zero
            isZoomed = false
            return
        }

        val scaleFactor = nextScale / previousScale
        val viewportCenter = viewportCenter()
        val centroidFromCenter = Offset(
            x = centroid.x - viewportCenter.x,
            y = centroid.y - viewportCenter.y,
        )
        val zoomAdjustedOffset = Offset(
            x = (offset.x + centroidFromCenter.x) * scaleFactor - centroidFromCenter.x,
            y = (offset.y + centroidFromCenter.y) * scaleFactor - centroidFromCenter.y,
        )

        scale = nextScale
        offset = clampOffset(
            candidate = Offset(
                x = zoomAdjustedOffset.x + nextScale * pan.x,
                y = zoomAdjustedOffset.y + nextScale * pan.y,
            ),
            targetScale = nextScale,
        )
        isZoomed = true
    }

    fun panBy(pan: Offset) {
        if (!isZoomed) return
        dismissOffsetY = 0f
        offset = clampOffset(
            candidate = Offset(
                x = offset.x + scale * pan.x,
                y = offset.y + scale * pan.y,
            ),
            targetScale = scale,
        )
    }

    fun toggleDoubleTapZoom(tapCentroid: Offset? = null, targetScale: Float = IMAGE_VIEWER_DEFAULT_DOUBLE_TAP_SCALE) {
        dismissOffsetY = 0f
        dismissOffsetX = 0f
        if (isZoomed) {
            resetTransform()
            return
        }
        val nextScale = normalizeScale(targetScale)
        val centroid = tapCentroid ?: viewportCenter()
        val viewportCenter = viewportCenter()

        val dx = centroid.x - viewportCenter.x
        val dy = centroid.y - viewportCenter.y

        scale = nextScale
        offset = clampOffset(
            candidate = Offset((1 - nextScale) * dx, (1 - nextScale) * dy),
            targetScale = nextScale,
        )
        isZoomed = scale > minScale
    }

    fun updateVerticalDismissOffset(dragAmount: Float) {
        if (isZoomed) return
        dismissOffsetY = max(0f, dismissOffsetY + dragAmount)
    }

    fun updateHorizontalDismissOffset(dragAmount: Float) {
        if (isZoomed) return
        val nextOffset = dismissOffsetX + dragAmount
        dismissOffsetX = if (dismissOffsetX == 0f) {
            nextOffset
        } else if (dismissOffsetX > 0f) {
            max(0f, nextOffset)
        } else {
            kotlin.math.min(0f, nextOffset)
        }
    }

    fun dismissProgress(widthPx: Float, heightPx: Float): Float {
        val verticalProgress = dismissOffsetY / heightPx.coerceAtLeast(1f)
        val horizontalProgress = abs(dismissOffsetX) / widthPx.coerceAtLeast(1f)
        return max(verticalProgress, horizontalProgress).coerceIn(0f, 1f)
    }

    fun currentImageRect(viewportWidthPx: Float, viewportHeightPx: Float): Rect {
        val dismissScale = 1f - (0.08f * dismissProgress(viewportWidthPx, viewportHeightPx))
        val currentScale = scale * dismissScale
        val baseSize = fittedContentSize()
        val displayWidth = baseSize.width * currentScale
        val displayHeight = baseSize.height * currentScale
        val centerX = viewportWidthPx / 2f + offset.x + dismissOffsetX
        val centerY = viewportHeightPx / 2f + offset.y + dismissOffsetY
        return Rect(
            left = centerX - displayWidth / 2f,
            top = centerY - displayHeight / 2f,
            right = centerX + displayWidth / 2f,
            bottom = centerY + displayHeight / 2f,
        )
    }

    fun resetDismiss() {
        dismissOffsetY = 0f
        dismissOffsetX = 0f
    }

    private fun resetTransform() {
        scale = minScale
        offset = Offset.Zero
        dismissOffsetY = 0f
        dismissOffsetX = 0f
        isZoomed = false
    }

    private fun normalizeScale(value: Float): Float {
        val clamped = value.coerceIn(minScale, maxScale)
        return if (clamped <= minScale + IMAGE_VIEWER_SCALE_EPSILON) minScale else clamped
    }

    private fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= minScale + IMAGE_VIEWER_SCALE_EPSILON) {
            return Offset.Zero
        }
        val baseSize = fittedContentSize()
        val maxX = (((baseSize.width * targetScale) - viewportSize.width) / 2f).coerceAtLeast(0f)
        val maxY = (((baseSize.height * targetScale) - viewportSize.height) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    private fun fittedContentSize(): Size {
        val viewportWidth = viewportSize.width.toFloat()
        val viewportHeight = viewportSize.height.toFloat()
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return Size.Zero
        }
        val imageWidth = imageSize.width.toFloat()
        val imageHeight = imageSize.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) {
            return Size(viewportWidth, viewportHeight)
        }
        val imageAspect = imageWidth / imageHeight
        val viewportAspect = viewportWidth / viewportHeight
        return if (imageAspect > viewportAspect) {
            Size(
                width = viewportWidth,
                height = viewportWidth / imageAspect,
            )
        } else {
            Size(
                width = viewportHeight * imageAspect,
                height = viewportHeight,
            )
        }
    }

    private fun viewportCenter(): Offset = Offset(
        x = viewportSize.width / 2f,
        y = viewportSize.height / 2f,
    )
}

private suspend fun PointerInputScope.detectImageViewerTransformGestures(
    pointCount: Int,
    panZoomLock: Boolean = false,
    pass: PointerEventPass = PointerEventPass.Main,
    onGestureStart: (PointerInputChange) -> Unit = {},
    onGesture: (
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotation: Float,
    ) -> Unit,
    onGestureEnd: (PointerInputChange) -> Unit = {},
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false
        var gestureStarted = false

        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = pass,
        )

        var pointer = down
        var pointerId = down.id

        do {
            val event = awaitPointerEvent(pass = pass)
            val currentPointerCount = event.changes.count { it.pressed }
            val canceled = event.changes.any { it.isConsumed } || currentPointerCount != pointCount

            if (!canceled) {
                if (pointCount >= 2) {
                    event.changes.forEach { it.consume() }
                }

                if (!gestureStarted) {
                    gestureStarted = true
                    onGestureStart(pointer)
                }

                val pointerInputChange = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.first()

                pointerId = pointerInputChange.id
                pointer = pointerInputChange

                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan = Offset(
                        x = pan.x + panChange.x,
                        y = pan.y + panChange.y,
                    )

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * kotlin.math.PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f || zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(
                            centroid,
                            panChange,
                            zoomChange,
                            effectiveRotation,
                        )
                    }

                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        if (gestureStarted) {
            onGestureEnd(pointer)
        }
    }
}

private suspend fun PointerInputScope.detectImageViewerVerticalDragGestures(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onVerticalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var overSlop = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        }
        if (drag != null && currentEvent.changes.count { it.pressed } == 1) {
            onDragStart(drag.position)
            onVerticalDrag(drag, overSlop)
            if (
                verticalDrag(drag.id) {
                    onVerticalDrag(it, it.positionChange().y)
                    it.consume()
                }
            ) {
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    }
}

private suspend fun PointerInputScope.detectImageViewerHorizontalEdgeDragGestures(
    viewportWidthPx: Float,
    edgeWidthPx: Float,
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onHorizontalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val leftEdge = down.position.x <= edgeWidthPx
        val rightEdge = down.position.x >= viewportWidthPx - edgeWidthPx
        if (!leftEdge && !rightEdge) {
            return@awaitEachGesture
        }

        var overSlop = 0f
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            val isClosingDirection = (leftEdge && over > 0f) || (rightEdge && over < 0f)
            if (isClosingDirection) {
                change.consume()
                overSlop = over
            }
        }
        val isClosingDirection = (leftEdge && overSlop > 0f) || (rightEdge && overSlop < 0f)
        if (drag != null && isClosingDirection && currentEvent.changes.count { it.pressed } == 1) {
            onDragStart(drag.position)
            onHorizontalDrag(drag, overSlop)
            if (
                horizontalDrag(drag.id) {
                    val dragAmount = it.positionChange().x
                    val keepsClosing = (leftEdge && dragAmount >= 0f) ||
                        (rightEdge && dragAmount <= 0f) ||
                        abs(dragAmount) < viewConfiguration.touchSlop
                    if (keepsClosing) {
                        onHorizontalDrag(it, dragAmount)
                        it.consume()
                    }
                }
            ) {
                onDragEnd()
            } else {
                onDragCancel()
            }
        }
    }
}
