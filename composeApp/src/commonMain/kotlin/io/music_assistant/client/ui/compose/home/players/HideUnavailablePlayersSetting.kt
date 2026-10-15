package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.settings.SettingsRepository
import org.koin.compose.koinInject

/** Reads the persisted "Hide unavailable" player list preference (default off); off under `@Preview` (no Koin graph). */
@Composable
fun rememberHideUnavailablePlayers(): Boolean {
    if (LocalInspectionMode.current) return false
    val settings: SettingsRepository = koinInject()
    return settings.hideUnavailablePlayers.collectAsStateWithLifecycle().value
}

/** No-op under `@Preview` (no Koin graph); otherwise flips the persisted flag against its live value. */
@Composable
fun rememberToggleHideUnavailablePlayers(): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val settings: SettingsRepository = koinInject()
    return { settings.setHideUnavailablePlayers(!settings.hideUnavailablePlayers.value) }
}
