package io.music_assistant.client.ui.compose.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalInspectionMode
import io.music_assistant.client.settings.SettingsRepository
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_show_waveform
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The "Show waveform" toggle of the player overflow menu. Reads and writes the persisted setting
 * directly, like [bufferIndicatorMenuOption]. A trailing check marks the on state. The caller
 * decides visibility: a track on a server that has waveforms.
 */
@Composable
fun waveformMenuOption(): OverflowMenuOption {
    if (LocalInspectionMode.current) {
        return OverflowMenuOption(
            title = stringResource(Res.string.player_show_waveform),
            icon = Icons.Default.GraphicEq,
            onClick = {},
        )
    }
    val settings: SettingsRepository = koinInject()
    val enabled = settings.showWaveform.collectAsState().value
    return OverflowMenuOption(
        title = stringResource(Res.string.player_show_waveform),
        icon = Icons.Default.GraphicEq,
        trailingIcon = Icons.Default.Check.takeIf { enabled },
        onClick = { settings.setShowWaveform(!settings.showWaveform.value) },
    )
}
