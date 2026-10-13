package io.music_assistant.client.ui.compose.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import io.music_assistant.client.settings.SeriesGrouping
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_series_grouping
import org.jetbrains.compose.resources.stringResource

/** Checked (tinted) while series are grouped, so the current state is visible without opening anything. */
@Composable
fun SeriesGroupingToggle(grouping: SeriesGrouping, onToggle: () -> Unit) {
    IconToggleButton(
        checked = grouping == SeriesGrouping.GROUPED,
        onCheckedChange = { onToggle() },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = stringResource(Res.string.cd_toggle_series_grouping),
        )
    }
}
