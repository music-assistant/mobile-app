package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.DataState
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.filter_none_selected
import musicassistantclient.composeapp.generated.resources.filter_selected_count
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A pickable option: [value] is what gets persisted/sent, [label] is what's shown. */
data class SelectOption<T>(val value: T, val label: String)

/**
 * A sheet row summarising a multi-select filter (label + "N selected"), tappable
 * to open the picker dialog. Rendered even before options load — the count comes
 * from the persisted selection, not the option list.
 */
@Composable
fun FilterPickerRow(
    label: StringResource,
    selectedCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(label), modifier = Modifier.weight(1f))
        Text(
            text = if (selectedCount > 0) {
                stringResource(Res.string.filter_selected_count, selectedCount)
            } else {
                stringResource(Res.string.filter_none_selected)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Multi-select dialog over [optionsState]. Selections accumulate in a working set
 * and commit on Done. Persisted [selected] values missing from the loaded options
 * (stale ids) are surfaced as extra rows so they remain removable.
 */
@Composable
fun <T> MultiSelectDialog(
    title: StringResource,
    optionsState: DataState<List<SelectOption<T>>>,
    selected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (optionsState) {
                    is DataState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                        CenteredProgress()
                    }

                    is DataState.Error -> CenteredText(
                        text = stringResource(Res.string.library_error),
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DataState.Data,
                    is DataState.Stale,
                        -> {
                        val options = optionsState.dataOrNull.orEmpty()
                        val known = remember(options) { options.map { it.value }.toSet() }
                        // Stale selections (persisted but no longer offered) as removable rows.
                        val shown = remember(options, selected) {
                            (selected - known).map { SelectOption(it, it.toString()) } + options
                        }

                        if (shown.isEmpty()) {
                            CenteredText(stringResource(Res.string.library_empty))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = MAX_DIALOG_HEIGHT)) {
                                items(items = shown, key = { it.value as Any }) { option ->
                                    OptionRow(
                                        label = option.label,
                                        checked = option.value in working,
                                        onToggle = {
                                            working = if (option.value in working) {
                                                working - option.value
                                            } else {
                                                working + option.value
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    is DataState.NoData -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                        CenteredProgress()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) {
                Text(stringResource(Res.string.common_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
