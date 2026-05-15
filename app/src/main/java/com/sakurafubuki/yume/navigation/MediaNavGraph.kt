package com.sakurafubuki.yume.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.sakurafubuki.yume.feature.player.PlayerActivity
import com.sakurafubuki.yume.feature.player.utils.PlayerApi
import com.sakurafubuki.yume.feature.videopicker.navigation.MediaPickerRoute
import com.sakurafubuki.yume.feature.videopicker.navigation.mediaPickerScreen
import com.sakurafubuki.yume.feature.videopicker.navigation.navigateToMediaPickerCloudPath
import com.sakurafubuki.yume.feature.videopicker.navigation.navigateToMediaPickerScreen
import com.sakurafubuki.yume.feature.videopicker.navigation.navigateToSearch
import com.sakurafubuki.yume.feature.videopicker.navigation.searchScreen
import kotlinx.serialization.Serializable

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
    onNavigateToSettingsTab: () -> Unit,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onCloudFolderClick = { cloudPath, cloudServerId ->
                navController.navigateToMediaPickerCloudPath(
                    cloudPath = cloudPath,
                    cloudServerId = cloudServerId,
                )
            },
            onCloudBackFromPath = { fallbackPath, cloudServerId ->
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigateToMediaPickerCloudPath(
                        cloudPath = fallbackPath,
                        cloudServerId = cloudServerId,
                    )
                }
            },
            onSettingsClick = onNavigateToSettingsTab,
            onSearchClick = navController::navigateToSearch,
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
        )
    }
}
