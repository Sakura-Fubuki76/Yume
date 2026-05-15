package com.sakurafubuki.yume.core.ui.motion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

data class OverlayEntry(
    val key: String,
    val zIndex: Float,
    val content: @Composable BoxScope.() -> Unit,
)

class OverlayContentState {
    private val entriesByKey = mutableStateMapOf<String, OverlayEntry>()
    private val keyOrder = mutableStateListOf<String>()

    val overlayEntries: List<OverlayEntry>
        get() {
            val ordered = keyOrder.mapNotNull(entriesByKey::get)
            return ordered.sortedBy(OverlayEntry::zIndex)
        }

    @Deprecated(
        message = "Use overlayEntries or keyed updateContent/clearContent APIs for multi-element overlays.",
    )
    val sharedElementContent: (@Composable BoxScope.() -> Unit)?
        get() = entriesByKey[SHARED_ELEMENT_KEY]?.content

    fun updateContent(
        key: String,
        zIndex: Float = 0f,
        content: (@Composable BoxScope.() -> Unit)?,
    ) {
        if (content == null) {
            clearContent(key)
            return
        }
        if (!entriesByKey.containsKey(key)) {
            keyOrder.add(key)
        }
        entriesByKey[key] = OverlayEntry(key = key, zIndex = zIndex, content = content)
    }

    fun clearContent(key: String) {
        entriesByKey.remove(key)
        keyOrder.remove(key)
    }

    fun clearAllContent() {
        entriesByKey.clear()
        keyOrder.clear()
    }

    @Deprecated(
        message = "Use updateContent(key, zIndex, content) instead.",
        replaceWith = ReplaceWith(
            expression = "updateContent(key = OverlayContentState.SHARED_ELEMENT_KEY, zIndex = 100f, content = content)",
        ),
    )
    fun updateSharedElementContent(content: (@Composable BoxScope.() -> Unit)?) {
        updateContent(key = SHARED_ELEMENT_KEY, zIndex = 100f, content = content)
    }

    @Deprecated(
        message = "Use clearContent(key) instead.",
        replaceWith = ReplaceWith(
            expression = "clearContent(key = OverlayContentState.SHARED_ELEMENT_KEY)",
        ),
    )
    fun clearSharedElementContent() {
        clearContent(SHARED_ELEMENT_KEY)
    }

    companion object {
        const val SHARED_ELEMENT_KEY = "shared_element"
    }
}
