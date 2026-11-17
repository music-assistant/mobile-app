package io.music_assistant.client.ui.theme

import androidx.lifecycle.ViewModel
import io.music_assistant.client.settings.SettingsRepository

class ThemeViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val theme = settingsRepository.theme
    fun switchTheme(theme: ThemeSetting) = settingsRepository.switchTheme(theme)
    val accentColor = settingsRepository.accentColor
    fun setAccentColor(accent: AccentColor) = settingsRepository.setAccentColor(accent)
}
