package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Drop the list's inner overscroll so a downward pull at the top reaches the
// ancestor collapse NestedScrollConnection instead of being eaten by the iOS
// Cupertino rubber-band. Covers both the populated LazyColumn and the empty
// verticalScroll Column.
@Composable
fun NoOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        content()
    }
}
