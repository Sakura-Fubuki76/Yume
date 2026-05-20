package com.sakurafubuki.yume.feature.imagebrowser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sakurafubuki.yume.core.model.ApplicationPreferences
import com.sakurafubuki.yume.core.model.MediaLayoutMode
import com.sakurafubuki.yume.core.model.MediaViewMode
import com.sakurafubuki.yume.core.model.Sort
import com.sakurafubuki.yume.core.ui.R
import com.sakurafubuki.yume.core.ui.components.CancelButton
import com.sakurafubuki.yume.core.ui.components.DoneButton
import com.sakurafubuki.yume.core.ui.components.NextDialog
import com.sakurafubuki.yume.core.ui.designsystem.NextIcons
import com.sakurafubuki.yume.feature.videopicker.composables.TextIconToggleButton
import com.sakurafubuki.yume.feature.videopicker.extensions.name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageQuickSettingsDialog(
    applicationPreferences: ApplicationPreferences,
    onDismiss: () -> Unit,
    updatePreferences: (ApplicationPreferences) -> Unit,
) {
    var preferences by remember {
        mutableStateOf(applicationPreferences.normalizeForImageQuickSettings())
    }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.image_quick_settings)) },
        content = {
            HorizontalDivider()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogSectionTitle(text = stringResource(R.string.image_view_mode))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val entries = listOf(MediaViewMode.IMAGE, MediaViewMode.FOLDERS, MediaViewMode.FOLDER_TREE)
                    entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.imageViewMode == mode,
                            onClick = { preferences = preferences.copy(imageViewMode = mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                        ) {
                            Text(text = mode.label())
                        }
                    }
                }

                DialogSectionTitle(text = stringResource(R.string.image_layout))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val entries = MediaLayoutMode.entries
                    entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.imageLayoutMode == mode,
                            onClick = { preferences = preferences.copy(imageLayoutMode = mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                        ) {
                            Text(text = mode.label())
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.sort))
                SortOptions(
                    selectedSortBy = preferences.imageSortBy,
                    onOptionSelected = { preferences = preferences.copy(imageSortBy = it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val entries = Sort.Order.entries
                    entries.forEachIndexed { index, order ->
                        SegmentedButton(
                            selected = preferences.imageSortOrder == order,
                            onClick = { preferences = preferences.copy(imageSortOrder = order) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                            icon = {
                                Icon(
                                    imageVector = if (order == Sort.Order.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                                    contentDescription = stringResource(R.string.ascending),
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        ) {
                            Text(text = order.name(sortBy = preferences.imageSortBy))
                        }
                    }
                }
            }
        },
        confirmButton = {
            DoneButton(
                onClick = {
                    updatePreferences(preferences)
                    onDismiss()
                },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun SortOptions(
    selectedSortBy: Sort.By,
    onOptionSelected: (Sort.By) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        TextIconToggleButton(
            text = stringResource(id = R.string.title),
            icon = NextIcons.Title,
            isSelected = selectedSortBy == Sort.By.TITLE,
            onClick = { onOptionSelected(Sort.By.TITLE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.date),
            icon = NextIcons.Calendar,
            isSelected = selectedSortBy == Sort.By.DATE,
            onClick = { onOptionSelected(Sort.By.DATE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.size),
            icon = NextIcons.Size,
            isSelected = selectedSortBy == Sort.By.SIZE,
            onClick = { onOptionSelected(Sort.By.SIZE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.location),
            icon = NextIcons.Location,
            isSelected = selectedSortBy == Sort.By.PATH,
            onClick = { onOptionSelected(Sort.By.PATH) },
        )
    }
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaViewMode.label(): String = when (this) {
    MediaViewMode.IMAGE -> stringResource(R.string.images)
    MediaViewMode.VIDEOS -> stringResource(R.string.images)
    MediaViewMode.FOLDERS -> stringResource(R.string.folders)
    MediaViewMode.FOLDER_TREE -> stringResource(R.string.tree)
}

@Composable
private fun MediaLayoutMode.label(): String = when (this) {
    MediaLayoutMode.LIST -> stringResource(R.string.list)
    MediaLayoutMode.GRID -> stringResource(R.string.grid)
}

private fun ApplicationPreferences.normalizeForImageQuickSettings(): ApplicationPreferences {
    val normalizedSortBy = if (imageSortBy == Sort.By.LENGTH) Sort.By.TITLE else imageSortBy
    return copy(
        imageSortBy = normalizedSortBy,
        imageViewMode = if (imageViewMode == MediaViewMode.VIDEOS) MediaViewMode.IMAGE else imageViewMode,
    )
}
