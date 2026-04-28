package io.music_assistant.client.ui.theme

import androidx.lifecycle.ViewModel
import io.music_assistant.client.settings.SettingsRepository

class ThemeViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val theme = settingsRepository.theme
    fun switchTheme(theme: ThemeSetting) = settingsRepository.switchTheme(theme)
}
