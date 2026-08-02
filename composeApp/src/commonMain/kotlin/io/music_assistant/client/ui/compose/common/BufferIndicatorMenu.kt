package io.music_assistant.client.ui.compose.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalInspectionMode
import io.music_assistant.client.settings.SettingsRepository
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_show_buffer_indicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Canonical "Show buffer indicator" toggle for the local player's overflow menu. Reads and
 * writes the single persisted preference directly (mirrors [dynamicColorsMenuOption]), so the
 * on-screen buffered-segment reacts immediately. A trailing check marks the on-state. The caller
 * decides visibility (local player only) — this just encapsulates the read/write.
 */
@Composable
fun bufferIndicatorMenuOption(): OverflowMenuOption {
    val enabled = rememberShowBufferIndicator()
    return OverflowMenuOption(
        title = stringResource(Res.string.player_show_buffer_indicator),
        icon = Icons.Default.Downloading,
        trailingIcon = Icons.Default.Check.takeIf { enabled },
        onClick = rememberToggleShowBufferIndicator(),
    )
}

@Composable
private fun rememberShowBufferIndicator(): Boolean {
    if (LocalInspectionMode.current) return true
    val settings: SettingsRepository = koinInject()
    return settings.showBufferVisualization.collectAsState().value
}

/** No-op under `@Preview` (no Koin graph); otherwise flips the persisted flag against its live value. */
@Composable
private fun rememberToggleShowBufferIndicator(): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val settings: SettingsRepository = koinInject()
    return { settings.setShowBufferVisualization(!settings.showBufferVisualization.value) }
}
