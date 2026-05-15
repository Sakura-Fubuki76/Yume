package com.sakurafubuki.yume.navigation

import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.sakurafubuki.yume.feature.imagebrowser.navigation.imageBrowserRoute
import com.sakurafubuki.yume.feature.imagebrowser.navigation.imageBrowserScreen
import com.sakurafubuki.yume.feature.imagebrowser.navigation.navigateToImageBrowser
import com.sakurafubuki.yume.feature.imagebrowser.ui.ImageViewerRoute
import com.sakurafubuki.yume.feature.imagebrowser.ui.ImageViewerStore

@Composable
fun AppNavHost(
    context: Context,
    pagerState: PagerState,
    mediaNavController: NavHostController,
    imageNavController: NavHostController,
    settingsNavController: NavHostController,
    onNavigateToSettingsTab: () -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    fun NavBackStackEntry.isImageViewerDestination(): Boolean = destination.route?.startsWith("image_viewer") == true

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier,
    ) { page ->
        when (page) {
            0 -> {
                NavHost(
                    navController = mediaNavController,
                    startDestination = MediaRootRoute,
                    enterTransition = { defaultEnterTransition() },
                    exitTransition = { defaultExitTransition() },
                    popEnterTransition = { defaultPopEnterTransition() },
                    popExitTransition = { defaultPopExitTransition() },
                ) {
                    mediaNavGraph(context, mediaNavController, onNavigateToSettingsTab)
                }
            }

            1 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = imageNavController,
                        startDestination = imageBrowserRoute,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            if (targetState.isImageViewerDestination()) {
                                EnterTransition.None
                            } else {
                                defaultEnterTransition()
                            }
                        },
                        exitTransition = {
                            if (targetState.isImageViewerDestination() || initialState.isImageViewerDestination()) {
                                ExitTransition.None
                            } else {
                                defaultExitTransition()
                            }
                        },
                        popEnterTransition = {
                            if (targetState.isImageViewerDestination() || initialState.isImageViewerDestination()) {
                                EnterTransition.None
                            } else {
                                defaultPopEnterTransition()
                            }
                        },
                        popExitTransition = {
                            if (initialState.isImageViewerDestination()) {
                                ExitTransition.None
                            } else {
                                defaultPopExitTransition()
                            }
                        },
                    ) {
                        imageBrowserScreen(
                            onNavigateToSettings = onNavigateToSettingsTab,
                            onNavigateToPath = { path, cloudServerId ->
                                imageNavController.navigateToImageBrowser(path = path, cloudServerId = cloudServerId)
                            },
                            onNavigateBackFromPath = { fallbackPath, cloudServerId ->
                                val popped = imageNavController.popBackStack()
                                if (!popped) {
                                    imageNavController.navigateToImageBrowser(path = fallbackPath, cloudServerId = cloudServerId)
                                }
                            },
                            onImageClick = ImageViewerStore::showViewer,
                        )
                    }

                    if (ImageViewerStore.isViewerShowing) {
                        ImageViewerRoute(
                            initialIndex = ImageViewerStore.viewerIndex,
                            onBack = ImageViewerStore::hideViewer,
                        )
                    }
                }
            }

            else -> {
                NavHost(
                    navController = settingsNavController,
                    startDestination = SETTINGS_ROUTE,
                    enterTransition = { defaultEnterTransition() },
                    exitTransition = { defaultExitTransition() },
                    popEnterTransition = { defaultPopEnterTransition() },
                    popExitTransition = { defaultPopExitTransition() },
                ) {
                    settingsNavGraph(navController = settingsNavController)
                }
            }
        }
    }
}

private const val TAB_TRANSITION_DURATION_MS = 280
private const val TAB_TRANSITION_POP_OFFSET_RATIO = 0.3f

private fun defaultEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = TAB_TRANSITION_DURATION_MS),
    initialOffsetX = { fullWidth -> fullWidth },
)

private fun defaultExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = TAB_TRANSITION_DURATION_MS),
    targetOffsetX = { fullWidth -> (-fullWidth * TAB_TRANSITION_POP_OFFSET_RATIO).toInt() },
)

private fun defaultPopEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = TAB_TRANSITION_DURATION_MS),
    initialOffsetX = { fullWidth -> (-fullWidth * TAB_TRANSITION_POP_OFFSET_RATIO).toInt() },
)

private fun defaultPopExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = TAB_TRANSITION_DURATION_MS),
    targetOffsetX = { fullWidth -> fullWidth },
)
