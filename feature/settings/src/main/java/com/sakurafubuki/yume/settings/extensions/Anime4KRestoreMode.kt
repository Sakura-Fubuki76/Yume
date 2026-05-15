package com.sakurafubuki.yume.settings.extensions

import com.sakurafubuki.yume.core.model.Anime4KRestoreMode

fun Anime4KRestoreMode.name(): String = when (this) {
    Anime4KRestoreMode.OFF -> "Off"
    Anime4KRestoreMode.L -> "Anime4K Restore CNN L"
    Anime4KRestoreMode.M -> "Anime4K Restore CNN M"
}
