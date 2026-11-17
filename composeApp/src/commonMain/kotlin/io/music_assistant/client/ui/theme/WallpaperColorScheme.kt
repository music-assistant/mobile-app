package io.music_assistant.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** The Material You scheme of the device wallpaper, or null if the platform has none. */
@Composable
expect fun wallpaperColorScheme(darkTheme: Boolean): ColorScheme?
