package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LibraryCategoriesViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(State(categories = buildInitialCategories()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.libraryCategoryConfig.collect { setting ->
                _state.update { it.copy(categories = getCategoryStates(setting)) }
            }
        }
    }

    fun onTabsConfigChanged(newOrder: List<Pair<LibraryCategory, Boolean>>) {
        settingsRepository.setLibraryCategoryConfig(
            newOrder.map { (tab, enabled) ->
                SettingsRepository.LibraryCategoryPref(name = tab.name, enabled = enabled)
            },
        )
    }

    private fun buildInitialCategories(): List<CategoryState> {
        val stored = settingsRepository.libraryCategoryConfig.value
        return getCategoryStates(stored)
    }

    private fun getCategoryStates(setting: List<SettingsRepository.LibraryCategoryPref>?): List<CategoryState> {
        return if (setting != null) {
            setting.map { CategoryState(LibraryCategory.valueOf(it.name), it.enabled) }
        } else {
            LibraryCategory.entries.map { CategoryState(it, true) }
        }
    }

    data class State(
        val categories: List<CategoryState>,
    )

    data class CategoryState(
        val libraryCategory: LibraryCategory,
        val enabled: Boolean,
    )
}
