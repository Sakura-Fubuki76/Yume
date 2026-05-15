package com.sakurafubuki.yume.core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sakurafubuki.yume.core.ui.R

@Composable
fun PermissionMissingView(
    isGranted: Boolean,
    showRationale: Boolean,
    permissions: List<String>,
    launchPermissionRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val permission = permissions.joinToString()
    if (isGranted) {
        content()
    } else if (showRationale) {
        PermissionRationaleDialog(
            text = stringResource(
                id = R.string.permission_info,
                permission,
            ),
            onConfirmButtonClick = launchPermissionRequest,
        )
    } else {
        PermissionDetailView(
            text = stringResource(
                id = R.string.permission_settings,
                permission,
            ),
        )
    }
}
