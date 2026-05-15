package com.sakurafubuki.yume.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.sakurafubuki.yume.core.model.ApplicationPreferences
import com.sakurafubuki.yume.core.model.Folder
import com.sakurafubuki.yume.core.model.MediaLayoutMode
import com.sakurafubuki.yume.core.model.MediaViewMode
import com.sakurafubuki.yume.core.ui.R
import com.sakurafubuki.yume.core.ui.components.ListSectionTitle
import com.sakurafubuki.yume.feature.videopicker.state.SelectionManager
import com.sakurafubuki.yume.feature.videopicker.state.rememberSelectionManager
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaView(
    rootFolder: Folder,
    preferences: ApplicationPreferences,
    showHeaders: Boolean = preferences.mediaViewMode == MediaViewMode.FOLDER_TREE,
    allowSelection: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    contentVerticalPadding: Dp = 8.dp,
    selectionManager: SelectionManager = rememberSelectionManager(),
    lazyGridState: LazyGridState = rememberLazyGridState(),
    onFolderClick: (String) -> Unit,
    onVideoClick: (Uri) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val seenVideos = remember { mutableSetOf<String>() }

    val folderMinWidth = 90.dp
    val videoMinWidth = 130.dp
    BoxWithConstraints {
        val contentHorizontalPadding = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 8.dp
            MediaLayoutMode.GRID -> 8.dp
        }
        val itemSpacing = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 2.dp
            MediaLayoutMode.GRID -> 2.dp
        }
        val maxWidth = this.maxWidth - (contentHorizontalPadding * 2) - itemSpacing
        val maxFolders = (maxWidth / folderMinWidth).toInt()
        val maxVideos = (maxWidth / videoMinWidth).toInt()
        val spans = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> lcm(maxFolders, maxVideos)
        }

        val singleFolderSpan = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxFolders
        }
        val singleVideoSpan = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxVideos
        }

        val showFolders = preferences.mediaViewMode != MediaViewMode.VIDEOS
        val showVideos = preferences.mediaViewMode != MediaViewMode.FOLDERS

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            columns = GridCells.Fixed(spans),
            contentPadding = contentPadding + PaddingValues(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (showFolders) {
                if (showHeaders && rootFolder.folderList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ListSectionTitle(text = stringResource(id = R.string.folders) + " (${rootFolder.folderList.size})")
                    }
                }
                itemsIndexed(
                    items = rootFolder.folderList,
                    key = { _, folder -> folder.path },
                    span = { _, _ -> GridItemSpan(singleFolderSpan) },
                ) { index, folder ->
                    val selected by remember { derivedStateOf { allowSelection && selectionManager.isFolderSelected(folder) } }
                    FolderItem(
                        folder = folder,
                        isRecentlyPlayedFolder = rootFolder.isRecentlyPlayedVideo(folder.recentlyPlayedVideo),
                        preferences = preferences,
                        selected = selected,
                        isFirstItem = index == 0,
                        isLastItem = index == rootFolder.folderList.lastIndex,
                        onClick = {
                            if (allowSelection && selectionManager.isInSelectionMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                selectionManager.toggleFolderSelection(folder)
                            } else {
                                onFolderClick(folder.path)
                            }
                        },
                        onLongClick = if (allowSelection) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectionManager.toggleFolderSelection(folder)
                            }
                        } else {
                            null
                        },
                    )
                }

                if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty() && showVideos) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                }
            }

            if (showVideos) {
                if (showHeaders && rootFolder.mediaList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ListSectionTitle(text = stringResource(id = R.string.videos) + " (${rootFolder.mediaList.size})")
                    }
                }

                itemsIndexed(
                    items = rootFolder.mediaList,
                    key = { _, video -> video.uriString },
                    span = { _, _ -> GridItemSpan(singleVideoSpan) },
                ) { index, video ->
                    val selected by remember { derivedStateOf { allowSelection && selectionManager.isVideoSelected(video) } }
                    VideoItem(
                        video = video,
                        preferences = preferences,
                        isRecentlyPlayedVideo = rootFolder.isRecentlyPlayedVideo(video),
                        isFirstItem = index == 0,
                        isLastItem = index == rootFolder.mediaList.lastIndex,
                        selected = selected,
                        onClick = {
                            if (allowSelection && selectionManager.isInSelectionMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                selectionManager.toggleVideoSelection(video)
                            } else {
                                onVideoClick(video.uriString.toUri())
                            }
                        },
                        onLongClick = if (allowSelection) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectionManager.toggleVideoSelection(video)
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.onVisibilityChanged { isVisible ->
                            if (isVisible && !seenVideos.contains(video.uriString)) {
                                seenVideos.add(video.uriString)
                                onVideoLoaded(video.uriString.toUri())
                            }
                        },
                    )
                }
            }
        }
    }
}

fun lcm(a: Int, b: Int): Int = abs(a * b) / gcd(a, b)

fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
