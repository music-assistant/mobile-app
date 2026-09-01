package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

/**
 * Keeps the screen on while [enabled] is true and this composable stays in the
 * composition. Leaving the composition restores the normal screen timeout, so callers
 * do not have to release anything by hand.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
