package io.music_assistant.client.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.utils.WindowClass

@Composable
fun rowImageSize() = when {
    WindowClass.isAtLeastMedium() -> 48.dp
    else -> 48.dp
}

@Composable
fun libraryItemMinWidth() = when {
    WindowClass.isAtLeastMedium() -> 360.dp
    else -> 240.dp
}
