package io.music_assistant.client.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemAppearance(isDarkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        // This LaunchedEffect will re-run every time isDarkTheme changes
        LaunchedEffect(isDarkTheme) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Set status bar icons to dark on light theme, and light on dark theme
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
        }
    }
}
