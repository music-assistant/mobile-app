package io.music_assistant.client.ui.compose

import androidx.compose.runtime.Composable

/** Provides the platform's preferred text size to Compose; iOS updates it on Dynamic Type changes. */
@Composable
expect fun ProvideDynamicTypeFontScale(content: @Composable () -> Unit)
