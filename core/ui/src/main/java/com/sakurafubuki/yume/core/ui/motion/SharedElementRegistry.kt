package com.sakurafubuki.yume.core.ui.motion

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

class SharedElementRegistry {
    private val boundsMap = mutableStateMapOf<String, Rect>()
    private val lastKnownBoundsMap = mutableStateMapOf<String, Rect>()

    fun updateBounds(id: String, rect: Rect) {
        boundsMap[id] = rect
        lastKnownBoundsMap[id] = rect
    }

    fun getBounds(id: String): Rect? = boundsMap[id]

    fun getLastKnownBounds(id: String): Rect? = lastKnownBoundsMap[id]

    fun removeBounds(id: String) {
        boundsMap.remove(id)
    }

    fun clear() {
        boundsMap.clear()
        lastKnownBoundsMap.clear()
    }
}
