package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.settings.BadgeVisibility
import io.music_assistant.client.settings.SettingsRepository
import org.koin.compose.koinInject

/**
 * The badges that the user wants to see. Every item tile and row reads it, so the choice is not
 * threaded through each screen. All badges show when nothing provides it.
 */
val LocalBadgeVisibility = compositionLocalOf { BadgeVisibility() }

/** Exposes [SettingsRepository.badgeVisibility] via [LocalBadgeVisibility]. Provide near the app root. */
@Composable
fun ProvideBadgeVisibility(content: @Composable () -> Unit) {
    val visibility by koinInject<SettingsRepository>().badgeVisibility.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalBadgeVisibility provides visibility, content)
}
