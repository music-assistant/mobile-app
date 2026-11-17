package io.music_assistant.client.ui.compose.item

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import io.music_assistant.client.settings.PlayedFilter
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_hide_played_episodes
import org.jetbrains.compose.resources.stringResource

/** Checked (tinted) while the played episodes are hidden, so the current state is visible without opening anything. */
@Composable
fun PlayedFilterToggle(filter: PlayedFilter, onToggle: () -> Unit) {
    IconToggleButton(
        checked = filter == PlayedFilter.HIDE_PLAYED,
        onCheckedChange = { onToggle() },
    ) {
        Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = stringResource(Res.string.cd_toggle_hide_played_episodes),
        )
    }
}
