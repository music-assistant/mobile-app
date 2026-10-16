package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.common.SettingsSheet
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_filter
import musicassistantclient.composeapp.generated.resources.filter_reset
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Filter button plus its sheet. [canReset] and [onReset] act on the working copy only:
 * the reset button shows while the working copy has active filters, and "Apply" still
 * commits the result.
 */
@Composable
fun <T> FilterAction(
    active: Boolean,
    state: () -> T,
    onApply: (T) -> Unit,
    canReset: (T) -> Boolean,
    onReset: (T) -> Unit,
    filters: @Composable ColumnScope.(state: T) -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    IconButton(
        modifier = Modifier.semantics {
            toggleableState = ToggleableState(active)
        },
        onClick = { showFilterSheet = true },
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = stringResource(Res.string.cd_filter),
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }

    if (showFilterSheet) {
        SettingsSheet(
            title = stringResource(Res.string.filter_sheet_title),
            state,
            onApply = {
                onApply(it)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        ) {
            filters(it)
            if (canReset(it)) {
                TextButton(
                    onClick = { onReset(it) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.filter_reset),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
