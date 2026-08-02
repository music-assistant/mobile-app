package io.music_assistant.client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun isSystemInDarkTheme(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()

@Composable
actual fun SystemAppearance(isDarkTheme: Boolean, followsSystem: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        // This LaunchedEffect will re-run every time isDarkTheme changes
        LaunchedEffect(isDarkTheme) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Set status/navigation bar icons to dark on light theme, and light on dark theme
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme

            // Let the app draw the navigation-bar background itself (the menu in normal mode,
            // the player gradient when expanded). Without this the system paints its own
            // translucent contrast scrim behind the buttons/handle whenever it deems the
            // content low-contrast, which reads as an off-theme "light" strip over the player.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
}
