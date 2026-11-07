package io.music_assistant.client.ui.theme

import androidx.compose.runtime.Composable

enum class ThemeSetting {
    Dark, Light, FollowSystem
}

/** The darkness that this setting gives on this device. */
@Composable
fun ThemeSetting.isDark(): Boolean = when (this) {
    ThemeSetting.Dark -> true
    ThemeSetting.Light -> false
    ThemeSetting.FollowSystem -> isSystemInDarkTheme()
}
