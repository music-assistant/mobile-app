package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun SortChip(
    currentSort: SortOption,
    availableFields: List<SortField>,
    onSortChanged: (SortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(currentSort.field.localizedName()) },
            trailingIcon = {
                Icon(
                    if (currentSort.descending) {
                        Icons.Default.ArrowDownward
                    } else {
                        Icons.Default.ArrowUpward
                    },
                    contentDescription = stringResource(Res.string.cd_sort_direction),
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        SortDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            availableFields = availableFields,
            onSortChanged = onSortChanged,
            currentSort = currentSort,
        )
    }
}

@Composable
fun SortDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    availableFields: List<SortField>,
    onSortChanged: (SortOption) -> Unit,
    currentSort: SortOption,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        availableFields.forEach { field ->
            DropdownMenuItem(
                text = { Text(field.localizedName()) },
                onClick = {
                    onDismissRequest()

                    onSortChanged(
                        if (field == currentSort.field) {
                            SortOption(field, !currentSort.descending)
                        } else {
                            SortOption(field)
                        },
                    )
                },
                trailingIcon = if (field == currentSort.field) {
                    {
                        Icon(
                            if (currentSort.descending) {
                                Icons.Default.ArrowUpward
                            } else {
                                Icons.Default.ArrowDownward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SortField.localizedName(): String = when (this) {
    SortField.ORIGINAL -> stringResource(Res.string.sort_original)
    SortField.NAME -> stringResource(Res.string.sort_name)
    SortField.DURATION -> stringResource(Res.string.sort_duration)
    SortField.DATE_ADDED -> stringResource(Res.string.sort_date_added)
    SortField.DATE_MODIFIED -> stringResource(Res.string.sort_date_modified)
    SortField.LAST_PLAYED -> stringResource(Res.string.sort_last_played)
    SortField.PLAY_COUNT -> stringResource(Res.string.sort_play_count)
    SortField.YEAR -> stringResource(Res.string.sort_year)
    SortField.POSITION -> stringResource(Res.string.sort_position)
    SortField.ARTIST_NAME -> stringResource(Res.string.sort_artist)
    SortField.RELEASE_DATE -> stringResource(Res.string.sort_release_date)
}
