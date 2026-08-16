package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import kotlinx.coroutines.flow.StateFlow

class ViewModeViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    fun viewModeFor(mediaType: MediaType): StateFlow<ViewMode> {
        return settingsRepository.viewMode(mediaType)
    }

    fun toggleFor(mediaType: MediaType) {
        val current = settingsRepository.viewMode(mediaType).value
        settingsRepository.setViewMode(mediaType, current.toggled())
    }
}
