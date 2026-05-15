package com.sakurafubuki.yume.core.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TransitionEngine {
    var progress by mutableFloatStateOf(0f)
        private set

    var type by mutableStateOf(TransitionType.None)
        private set

    var direction by mutableStateOf(Direction.Forward)
        private set

    var isGestureDriven by mutableStateOf(false)
        private set

    var dimEnabled by mutableStateOf(true)

    val isRunning: Boolean
        get() = type != TransitionType.None

    fun start(
        type: TransitionType,
        direction: Direction,
        initialProgress: Float = 0f,
        isGestureDriven: Boolean = false,
    ) {
        this.type = type
        this.direction = direction
        this.progress = initialProgress.coerceIn(0f, 1f)
        this.isGestureDriven = isGestureDriven
    }

    fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    fun finish() {
        progress = 0f
        type = TransitionType.None
        direction = Direction.Forward
        isGestureDriven = false
    }

    fun dimAlpha(): Float {
        if (!isRunning || !dimEnabled) return 0f
        val easedProgress = FastOutSlowInEasing.transform(progress)
        val maxDimAlpha = when (type) {
            TransitionType.SharedElement,
            TransitionType.PredictiveBack,
            -> 0.22f
            else -> 0.62f
        }
        return when (direction) {
            Direction.Forward -> maxDimAlpha * easedProgress
            Direction.Backward -> maxDimAlpha * (1f - easedProgress)
        }
    }
}

enum class TransitionType {
    None,
    Navigation,
    SharedElement,
    PredictiveBack,
}

enum class Direction {
    Forward,
    Backward,
}
