package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

/**
 * Hides the status and navigation bars while this composable stays in the composition, for a
 * screen that is there to be looked at. A swipe from the edge shows them for a moment. Leaving
 * the composition shows them again, so callers do not have to restore anything by hand.
 */
@Composable
expect fun SystemBarsHidden()
