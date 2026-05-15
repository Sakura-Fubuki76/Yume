package com.sakurafubuki.yume.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.sakurafubuki.yume.settings.screens.performance.PerformancePreferencesScreen

const val performancePreferencesNavigationRoute = "performance_preferences_route"

fun NavController.navigateToPerformancePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(performancePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.performancePreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = performancePreferencesNavigationRoute) {
        PerformancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
