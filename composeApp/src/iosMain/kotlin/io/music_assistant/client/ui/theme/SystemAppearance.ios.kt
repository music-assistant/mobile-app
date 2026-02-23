package io.music_assistant.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
actual fun SystemAppearance(isDarkTheme: Boolean) {
    LaunchedEffect(isDarkTheme) {
        val style = if (isDarkTheme) UIUserInterfaceStyle.Dark else UIUserInterfaceStyle.Light
        UIApplication.sharedApplication.connectedScenes.forEach { scene ->
            (scene as? UIWindowScene)?.windows?.forEach { window ->
                (window as? UIWindow)?.overrideUserInterfaceStyle = style
            }
        }
    }
}

