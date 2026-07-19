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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_apply
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Sheet height as a fraction of the screen — matches the "80% height" spec. */
private const val SHEET_HEIGHT_FRACTION = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            content()
        }
    }
}

object SettingsSheet {
    @Composable
    fun Header(onApply: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 24.dp, top = 12.dp, bottom = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.filter_sheet_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onApply) {
                Text(stringResource(Res.string.common_apply))
            }
        }
    }

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
                .padding(vertical = 8.dp),
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
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(stringResource(optionLabel(option))) },
                )
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
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(stringResource(optionLabel(option))) },
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChoiceSection(
        label: StringResource,
        chips: @Composable () -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = stringResource(label),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { chips() }
        }
    }
}
