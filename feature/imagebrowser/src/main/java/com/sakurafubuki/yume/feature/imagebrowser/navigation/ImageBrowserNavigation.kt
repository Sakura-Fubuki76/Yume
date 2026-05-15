package com.sakurafubuki.yume.feature.imagebrowser.navigation

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sakurafubuki.yume.feature.imagebrowser.ui.ImageBrowserRoute
import com.sakurafubuki.yume.feature.imagebrowser.ui.ImageViewerRoute

const val imageBrowserRoute = "image_browser"
const val imageBrowserPathArg = "path"
const val imageBrowserCloudServerIdArg = "cloudServerId"
private const val imageBrowserRoutePattern =
    "$imageBrowserRoute?$imageBrowserPathArg={$imageBrowserPathArg}&$imageBrowserCloudServerIdArg={$imageBrowserCloudServerIdArg}"
private const val imageViewerRoute = "image_viewer/{index}"

fun NavController.navigateToImageBrowser(
    path: String = "/",
    cloudServerId: Int? = null,
    navOptions: NavOptions? = null,
) {
    val normalizedTargetPath = Uri.decode(path).ifBlank { "/" }
    val currentEntry = currentBackStackEntry
    val currentPath = currentEntry
        ?.arguments
        ?.getString(imageBrowserPathArg)
        ?.let(Uri::decode)
        .orEmpty()
        .ifBlank { "/" }
    val currentCloudServerId = currentEntry
        ?.arguments
        ?.getString(imageBrowserCloudServerIdArg)
        ?.toIntOrNull()

    if (
        currentEntry?.destination?.route?.startsWith(imageBrowserRoute) == true &&
        currentPath == normalizedTargetPath &&
        currentCloudServerId == cloudServerId
    ) {
        return
    }

    val encodedPath = Uri.encode(normalizedTargetPath)
    navigate(
        "$imageBrowserRoute?$imageBrowserPathArg=$encodedPath&$imageBrowserCloudServerIdArg=${cloudServerId ?: ""}",
        navOptions,
    )
}

fun NavController.navigateToImageViewer(index: Int) {
    navigate("image_viewer/$index")
}

fun NavGraphBuilder.imageBrowserScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPath: (String, Int?) -> Unit,
    onNavigateBackFromPath: (String, Int?) -> Unit,
    onImageClick: (Int) -> Unit,
) {
    composable(
        route = imageBrowserRoutePattern,
        arguments = listOf(
            navArgument(imageBrowserPathArg) {
                type = NavType.StringType
                defaultValue = "/"
            },
            navArgument(imageBrowserCloudServerIdArg) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        val routePath = it.arguments
            ?.getString(imageBrowserPathArg)
            ?.let(Uri::decode)
            .orEmpty()
            .ifBlank { "/" }
        val routeCloudServerId = it.arguments
            ?.getString(imageBrowserCloudServerIdArg)
            ?.toIntOrNull()
        ImageBrowserRoute(
            routePath = routePath,
            routeCloudServerId = routeCloudServerId,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPath = onNavigateToPath,
            onNavigateBackFromPath = onNavigateBackFromPath,
            onImageClick = onImageClick,
        )
    }
}

fun NavGraphBuilder.imageViewerScreen(
    onViewerEntered: (Int) -> Unit,
    onNavigateUp: () -> Unit,
) {
    composable(route = imageViewerRoute) {
        val index = it.arguments?.getString("index")?.toIntOrNull() ?: 0
        LaunchedEffect(index) {
            onViewerEntered(index)
        }
        ImageViewerRoute(initialIndex = index, onBack = onNavigateUp)
    }
}
