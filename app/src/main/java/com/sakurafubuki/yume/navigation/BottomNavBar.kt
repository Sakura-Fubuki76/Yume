package com.sakurafubuki.yume.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sakurafubuki.yume.R
import com.sakurafubuki.yume.core.ui.designsystem.NextIcons

@Composable
fun AppBottomNavBar(
    selectedScreen: Screen,
    onNavigate: (Screen) -> Unit,
) {
    val items = listOf(
        BottomNavDestination(
            screen = Screen.Video,
            icon = NextIcons.Video,
            labelRes = R.string.bottom_nav_video,
        ),
        BottomNavDestination(
            screen = Screen.Image,
            icon = NextIcons.Image,
            labelRes = R.string.bottom_nav_images,
        ),
        BottomNavDestination(
            screen = Screen.Settings,
            icon = NextIcons.Settings,
            labelRes = R.string.bottom_nav_settings,
        ),
    )

    NavigationBar(
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        items.forEach { item ->
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = selectedScreen.route == item.screen.route,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(text = label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(),
            )
        }
    }
}

private data class BottomNavDestination(
    val screen: Screen,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @param:StringRes val labelRes: Int,
)
