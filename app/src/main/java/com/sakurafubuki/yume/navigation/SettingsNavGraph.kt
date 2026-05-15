package com.sakurafubuki.yume.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.sakurafubuki.yume.settings.Setting
import com.sakurafubuki.yume.settings.navigation.aboutPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.appearancePreferencesScreen
import com.sakurafubuki.yume.settings.navigation.audioPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.decoderPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.folderPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.generalPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.gesturePreferencesScreen
import com.sakurafubuki.yume.settings.navigation.librariesScreen
import com.sakurafubuki.yume.settings.navigation.mediaLibraryPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.navigateToAboutPreferences
import com.sakurafubuki.yume.settings.navigation.navigateToAppearancePreferences
import com.sakurafubuki.yume.settings.navigation.navigateToAudioPreferences
import com.sakurafubuki.yume.settings.navigation.navigateToDecoderPreferences
import com.sakurafubuki.yume.settings.navigation.navigateToFolderPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.navigateToGeneralPreferences
import com.sakurafubuki.yume.settings.navigation.navigateToGesturePreferences
import com.sakurafubuki.yume.settings.navigation.navigateToLibraries
import com.sakurafubuki.yume.settings.navigation.navigateToMediaLibraryPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.navigateToPerformancePreferences
import com.sakurafubuki.yume.settings.navigation.navigateToPlayerPreferences
import com.sakurafubuki.yume.settings.navigation.navigateToSubtitlePreferences
import com.sakurafubuki.yume.settings.navigation.navigateToThumbnailPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.performancePreferencesScreen
import com.sakurafubuki.yume.settings.navigation.playerPreferencesScreen
import com.sakurafubuki.yume.settings.navigation.settingsNavigationRoute
import com.sakurafubuki.yume.settings.navigation.settingsScreen
import com.sakurafubuki.yume.settings.navigation.subtitlePreferencesScreen
import com.sakurafubuki.yume.settings.navigation.thumbnailPreferencesScreen

const val SETTINGS_ROUTE = "settings_nav_route"

fun NavGraphBuilder.settingsNavGraph(
    navController: NavHostController,
) {
    navigation(
        startDestination = settingsNavigationRoute,
        route = SETTINGS_ROUTE,
    ) {
        settingsScreen(
            onNavigateUp = null,
            onItemClick = { setting ->
                when (setting) {
                    Setting.APPEARANCE -> navController.navigateToAppearancePreferences()
                    Setting.MEDIA_LIBRARY -> navController.navigateToMediaLibraryPreferencesScreen()
                    Setting.PLAYER -> navController.navigateToPlayerPreferences()
                    Setting.GESTURES -> navController.navigateToGesturePreferences()
                    Setting.DECODER -> navController.navigateToDecoderPreferences()
                    Setting.AUDIO -> navController.navigateToAudioPreferences()
                    Setting.SUBTITLE -> navController.navigateToSubtitlePreferences()
                    Setting.PERFORMANCE -> navController.navigateToPerformancePreferences()
                    Setting.GENERAL -> navController.navigateToGeneralPreferences()
                    Setting.ABOUT -> navController.navigateToAboutPreferences()
                }
            },
        )
        appearancePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        mediaLibraryPreferencesScreen(
            onNavigateUp = navController::navigateUp,
            onFolderSettingClick = navController::navigateToFolderPreferencesScreen,
            onThumbnailSettingClick = navController::navigateToThumbnailPreferencesScreen,
        )
        thumbnailPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        folderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        playerPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        gesturePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        decoderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        audioPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        subtitlePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        performancePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        generalPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        aboutPreferencesScreen(
            onLibrariesClick = navController::navigateToLibraries,
            onNavigateUp = navController::navigateUp,
        )
        librariesScreen(
            onNavigateUp = navController::navigateUp,
        )
    }
}
