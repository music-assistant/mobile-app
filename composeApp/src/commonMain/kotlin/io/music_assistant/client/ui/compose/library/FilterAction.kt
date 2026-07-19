package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.music_assistant.client.ui.compose.common.SettingsSheet
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_filter
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun FilterAction(
    active: Boolean,
    onApply: () -> Unit,
    filters: @Composable ColumnScope.() -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showFilterSheet = true }) {
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
            onApply = {
                onApply()
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        ) {
            filters()
        }
    }
}
