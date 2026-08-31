package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LibraryCategoriesViewModel(
    private val settingsRepository: SettingsRepository,
    private val dataSource: MainDataSource,
) : ViewModel() {
    /**
     * The last reconciled config, INCLUDING the categories filtered out of [state]. Kept so a
     * save can merge the hidden ones back instead of dropping them — see [mergeHiddenCategories].
     */
    private var reconciled: List<Pair<LibraryCategory, Boolean>> = emptyList()

    private val _state = MutableStateFlow(State(categories = buildInitialCategories()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.libraryCategoryConfig,
                dataSource.aiRadioAvailable,
            ) { setting, aiRadioAvailable -> getCategoryStates(setting, aiRadioAvailable) }
                .collect { categories -> _state.update { it.copy(categories = categories) } }
        }
    }

    fun onTabsConfigChanged(newOrder: List<Pair<LibraryCategory, Boolean>>) {
        settingsRepository.setLibraryCategoryConfig(
            mergeHiddenCategories(newOrder, reconciled).map { (tab, enabled) ->
                SettingsRepository.LibraryCategoryPref(name = tab.name, enabled = enabled)
            },
        )
    }

    private fun buildInitialCategories(): List<CategoryState> = getCategoryStates(
        settingsRepository.libraryCategoryConfig.value,
        dataSource.aiRadioAvailable.value,
    )

    private fun getCategoryStates(
        setting: List<SettingsRepository.LibraryCategoryPref>?,
        aiRadioAvailable: Boolean,
    ): List<CategoryState> {
        reconciled = reconcileLibraryCategories(setting)
        return visibleCategories(reconciled, aiRadioAvailable)
            .map { (category, enabled) -> CategoryState(category, enabled) }
    }

    data class State(
        val categories: List<CategoryState>,
    )

    data class CategoryState(
        val libraryCategory: LibraryCategory,
        val enabled: Boolean,
    )
}
