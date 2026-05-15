package com.sakurafubuki.yume.feature.videopicker.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.sakurafubuki.yume.feature.videopicker.screens.mediapicker.MediaPickerRoute
import kotlinx.serialization.Serializable

internal const val folderIdArg = "folderId"
internal const val cloudPathArg = "cloudPath"
internal const val cloudServerIdArg = "cloudServerId"

internal class FolderArgs(val folderId: String?) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(savedStateHandle.get<String>(folderIdArg)?.let { Uri.decode(it) })
}

internal class CloudPathArgs(
    val cloudPath: String?,
    val cloudServerId: Int?,
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            cloudPath = savedStateHandle.get<String>(cloudPathArg)?.let { Uri.decode(it) },
            cloudServerId = savedStateHandle.get<Int?>(cloudServerIdArg),
        )
}

@Serializable
data class MediaPickerRoute(
    val folderId: String? = null,
    val cloudPath: String? = null,
    val cloudServerId: Int? = null,
)

fun NavController.navigateToMediaPickerScreen(
    folderId: String,
    navOptions: NavOptions? = null,
) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate(MediaPickerRoute(encodedFolderId), navOptions)
}

fun NavController.navigateToMediaPickerCloudPath(
    cloudPath: String,
    cloudServerId: Int?,
    navOptions: NavOptions? = null,
) {
    val normalizedTargetPath = Uri.decode(cloudPath).ifBlank { "/" }
    val currentEntry = currentBackStackEntry
    val currentCloudPath = currentEntry
        ?.arguments
        ?.getString(cloudPathArg)
        ?.let(Uri::decode)
        .orEmpty()
        .ifBlank { "/" }
    val currentCloudServerId = currentEntry
        ?.arguments
        ?.getInt(cloudServerIdArg)

    if (
        currentEntry?.destination?.route?.contains("MediaPickerRoute") == true &&
        currentCloudPath == normalizedTargetPath &&
        currentCloudServerId == cloudServerId
    ) {
        return
    }

    navigate(
        MediaPickerRoute(
            cloudPath = Uri.encode(normalizedTargetPath),
            cloudServerId = cloudServerId,
        ),
        navOptions,
    )
}

fun NavGraphBuilder.mediaPickerScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onCloudFolderClick: (cloudPath: String, cloudServerId: Int?) -> Unit,
    onCloudBackFromPath: (fallbackPath: String, cloudServerId: Int?) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    composable<MediaPickerRoute> {
        MediaPickerRoute(
            onPlayVideo = onPlayVideo,
            onPlayVideos = onPlayVideos,
            onNavigateUp = onNavigateUp,
            onFolderClick = onFolderClick,
            onCloudFolderClick = onCloudFolderClick,
            onCloudBackFromPath = onCloudBackFromPath,
            onSettingsClick = onSettingsClick,
            onSearchClick = onSearchClick,
        )
    }
}
