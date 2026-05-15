package com.sakurafubuki.yume.navigation

sealed class Screen(val route: String) {
    data object Video : Screen("video")
    data object Image : Screen("image")
    data object Settings : Screen("settings")
    data object ImageViewer : Screen("image_viewer/{index}") {
        fun createRoute(index: Int) = "image_viewer/$index"
    }
}
