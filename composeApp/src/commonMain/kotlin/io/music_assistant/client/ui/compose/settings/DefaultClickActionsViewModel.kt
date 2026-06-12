package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.SettingsRepository

class DefaultClickActionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val actions = settingsRepository.defaultClickActions
    fun save(kind: ItemKind, perContext: Map<ClickContext, DefaultClickOption>) =
        settingsRepository.setDefaultClickActions(kind, perContext)
}
