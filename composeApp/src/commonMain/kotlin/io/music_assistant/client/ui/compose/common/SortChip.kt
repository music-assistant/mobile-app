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
            label = { Text(currentSort.field.displayName) },
            trailingIcon = if (currentSort.field != SortField.ORIGINAL) {
                {
                    Icon(
                        if (currentSort.descending) Icons.Default.ArrowDownward
                        else Icons.Default.ArrowUpward,
                        contentDescription = "Sort direction",
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else null
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableFields.forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.displayName) },
                    onClick = {
                        expanded = false
                        if (field == SortField.ORIGINAL) {
                            onSortChanged(SortOption(field))
                        } else if (field == currentSort.field) {
                            onSortChanged(SortOption(field, !currentSort.descending))
                        } else {
                            onSortChanged(SortOption(field))
                        }
                    },
                    trailingIcon = if (field == currentSort.field && field != SortField.ORIGINAL) {
                        {
                            Icon(
                                if (currentSort.descending) Icons.Default.ArrowUpward
                                else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null
                )
            }
        }
    }
}
