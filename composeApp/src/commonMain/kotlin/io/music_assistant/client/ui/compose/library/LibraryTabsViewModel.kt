package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LibraryTabsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    enum class Tab {
        ARTISTS, ALBUMS, TRACKS, PLAYLISTS, AUDIOBOOKS, PODCASTS, RADIOS, GENRES;

        val mediaType: MediaType
            get() = when (this) {
                ARTISTS -> MediaType.ARTIST
                ALBUMS -> MediaType.ALBUM
                TRACKS -> MediaType.TRACK
                PLAYLISTS -> MediaType.PLAYLIST
                AUDIOBOOKS -> MediaType.AUDIOBOOK
                PODCASTS -> MediaType.PODCAST
                RADIOS -> MediaType.RADIO
                GENRES -> MediaType.GENRE
            }
    }

    data class State(
        val tabs: List<TabState>,
    )

    data class TabState(
        val tab: Tab,
        val enabled: Boolean,
    )

    private val _state = MutableStateFlow(State(tabs = buildInitialTabs()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.libraryTabsConfig.collect { setting ->
                _state.update { it.copy(tabs = getTabStates(setting)) }
            }
        }
    }

    fun onTabsConfigChanged(newOrder: List<Pair<Tab, Boolean>>) {
        settingsRepository.setLibraryTabsConfig(
            newOrder.map { (tab, enabled) ->
                SettingsRepository.LibraryTabPref(name = tab.name, enabled = enabled)
            },
        )
    }

    private fun buildInitialTabs(): List<TabState> {
        val stored = settingsRepository.libraryTabsConfig.value
        return getTabStates(stored)
    }

    private fun getTabStates(setting: List<SettingsRepository.LibraryTabPref>?): List<TabState> {
        return if (setting != null) {
            setting.map { TabState(Tab.valueOf(it.name), it.enabled) }
        } else {
            Tab.entries.map { TabState(it, true) }
        }
    }
}
