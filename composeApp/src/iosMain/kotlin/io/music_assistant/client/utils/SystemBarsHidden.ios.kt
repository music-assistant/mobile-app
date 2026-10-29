package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBarsHidden() {
    // The status bar belongs to the SwiftUI host (ContentView.swift), out of reach from here,
    // so it stays. The screen under it still draws edge to edge.
}
