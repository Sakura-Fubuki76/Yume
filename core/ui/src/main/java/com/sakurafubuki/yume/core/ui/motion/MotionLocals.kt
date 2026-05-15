package com.sakurafubuki.yume.core.ui.motion

import androidx.compose.runtime.compositionLocalOf

val LocalTransitionEngine = compositionLocalOf<TransitionEngine> {
    error("No TransitionEngine provided")
}

val LocalSharedElementRegistry = compositionLocalOf<SharedElementRegistry> {
    error("No SharedElementRegistry provided")
}

val LocalOverlayContentState = compositionLocalOf<OverlayContentState> {
    error("No OverlayContentState provided")
}
