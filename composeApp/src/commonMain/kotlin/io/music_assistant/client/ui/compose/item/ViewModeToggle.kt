package io.music_assistant.client.ui.compose.item

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import io.music_assistant.client.settings.ViewMode
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import org.jetbrains.compose.resources.stringResource

@Composable
fun ViewModeToggle(viewMode: ViewMode, onToggleViewMode: () -> Unit) {
    IconButton(onClick = onToggleViewMode) {
        Icon(
            imageVector = when (viewMode) {
                ViewMode.LIST -> Icons.Default.GridView
                ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
            },
            contentDescription = stringResource(Res.string.cd_toggle_view_mode),
        )
    }
}
