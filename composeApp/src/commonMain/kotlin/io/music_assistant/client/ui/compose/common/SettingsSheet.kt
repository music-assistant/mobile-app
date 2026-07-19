package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_apply
import musicassistantclient.composeapp.generated.resources.filter_none_selected
import musicassistantclient.composeapp.generated.resources.filter_selected_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Sheet height as a fraction of the screen — matches the "80% height" spec. */
private const val SHEET_HEIGHT_FRACTION = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    title: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Non-swipeable: kills drag/swipe-to-dismiss while keeping scrim + back
        // (both routed through onDismissRequest). confirmValueChange would wrongly
        // block those too.
        sheetGesturesEnabled = false,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            Header(title = title, onApply = onApply)
            content()
        }
    }
}

@Composable
private fun Header(title: String, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, bottom = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onApply) {
            Text(stringResource(Res.string.common_apply))
        }
    }
}

object SettingsSheet {
    @Composable
    fun SwitchRow(
        label: StringResource,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onChange(!checked)
                }
                .padding(vertical = 8.dp, horizontal = ROW_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(label),
                modifier = Modifier.weight(1f),
            )

            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun <T> SingleChoiceChipsRow(
        label: StringResource,
        options: List<T>,
        selected: T,
        optionLabel: (T) -> StringResource,
        onSelect: (T) -> Unit,
    ) {
        ChoiceSection(label) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp,
                        start = ROW_HORIZONTAL_PADDING,
                        end = ROW_HORIZONTAL_PADDING,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(stringResource(optionLabel(option))) },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun <T> MultiChoiceChipsRow(
        label: StringResource,
        options: List<T>,
        selected: List<T>,
        optionLabel: (T) -> StringResource,
        onToggle: (T) -> Unit,
    ) {
        ChoiceSection(label) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp,
                        start = ROW_HORIZONTAL_PADDING,
                        end = ROW_HORIZONTAL_PADDING,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val text = stringResource(optionLabel(option))

                    FilterChip(
                        modifier = Modifier.semantics {
                            contentDescription = "Filter $text"
                        },
                        selected = option in selected,
                        onClick = { onToggle(option) },
                        label = { Text(text) },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChoiceSection(
        label: StringResource,
        content: @Composable () -> Unit,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING),
                text = stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            content()
        }
    }

    /**
     * A sheet row summarising a multi-select option (label + "N selected"), tappable
     * to open the picker dialog. Rendered even before options load — the count comes
     * from the persisted selection, not the option list.
     */
    @Composable
    fun PickerRow(
        label: StringResource,
        selectedCount: Int,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = ROW_HORIZONTAL_PADDING),
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

    private val ROW_HORIZONTAL_PADDING = 24.dp
}
