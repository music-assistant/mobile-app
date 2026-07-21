package io.music_assistant.client.ui.compose.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import io.music_assistant.client.settings.SettingsRepository
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_dynamic_colors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Canonical "Dynamic colors" toggle for any overflow menu. Reads and writes the single
 * persisted preference directly, so every surface (player pager, item detail) shares one
 * control and one code path — no per-surface handler. A trailing check marks the on-state.
 */
@Composable
fun dynamicColorsMenuOption(): OverflowMenuOption {
    val enabled = rememberDynamicColorsEnabled()
    val toggle = rememberToggleDynamicColors()
    return OverflowMenuOption(
        title = stringResource(Res.string.player_dynamic_colors),
        icon = Icons.Default.Palette,
        trailingIcon = Icons.Default.Check.takeIf { enabled },
        onClick = toggle,
    )
}

/** No-op under `@Preview` (no Koin graph); otherwise flips the persisted flag against its live value. */
@Composable
private fun rememberToggleDynamicColors(): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val settings: SettingsRepository = koinInject()
    return { settings.setDynamicColors(!settings.dynamicColors.value) }
}
