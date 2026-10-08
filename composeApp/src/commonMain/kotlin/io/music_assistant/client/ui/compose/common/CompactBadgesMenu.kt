package io.music_assistant.client.ui.compose.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.settings.SettingsRepository
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_compact_badges
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Compact badges" toggle for the player overflow menu: icon-only status badges. Reads and
 * writes the single persisted preference directly (mirrors [dynamicColorsMenuOption]). A
 * trailing check marks the on-state.
 */
@Composable
fun compactBadgesMenuOption(): OverflowMenuOption {
    val enabled = rememberCompactPlayerBadges()
    return OverflowMenuOption(
        title = stringResource(Res.string.player_compact_badges),
        icon = Icons.Default.ViewCompact,
        trailingIcon = Icons.Default.Check.takeIf { enabled },
        onClick = rememberToggleCompactPlayerBadges(),
    )
}

/** Reads the persisted "Compact badges" preference (default off). Previews get the default. */
@Composable
fun rememberCompactPlayerBadges(): Boolean {
    if (LocalInspectionMode.current) return false
    val settings: SettingsRepository = koinInject()
    return settings.compactPlayerBadges.collectAsStateWithLifecycle().value
}

/** No-op under `@Preview` (no Koin graph); otherwise flips the persisted flag against its live value. */
@Composable
private fun rememberToggleCompactPlayerBadges(): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val settings: SettingsRepository = koinInject()
    return { settings.setCompactPlayerBadges(!settings.compactPlayerBadges.value) }
}
