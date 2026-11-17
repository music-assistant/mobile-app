package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.repository.PartyRepository
import io.music_assistant.client.nav.SidebarEdit
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
    private val partyRepository: PartyRepository,
) : ViewModel() {
    /**
     * The last reconciled config, INCLUDING the categories filtered out of [state]. Kept so a
     * save keeps their stored flag instead of turning them off — see [applySidebarEdit].
     */
    private var reconciled: List<Pair<LibraryCategory, Boolean>> = emptyList()

    private val _state = MutableStateFlow(State(categories = buildInitialCategories()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.libraryCategoryConfig,
                dataSource.aiRadioAvailable,
                partyRepository.available,
            ) { setting, aiRadioAvailable, partyAvailable ->
                getCategoryStates(setting, aiRadioAvailable, partyAvailable)
            }
                .collect { categories -> _state.update { it.copy(categories = categories) } }
        }
    }

    fun onSidebarEdited(edit: SidebarEdit) {
        val visible = _state.value.categories.mapTo(HashSet()) { it.libraryCategory }
        settingsRepository.setLibraryCategoryConfig(
            applySidebarEdit(reconciled, visible, edit.enabledIds, edit.orderedIds).map { (category, enabled) ->
                SettingsRepository.LibraryCategoryPref(category.name, enabled)
            },
        )
    }

    private fun buildInitialCategories(): List<CategoryState> = getCategoryStates(
        settingsRepository.libraryCategoryConfig.value,
        dataSource.aiRadioAvailable.value,
        partyRepository.available.value,
    )

    private fun getCategoryStates(
        setting: List<SettingsRepository.LibraryCategoryPref>?,
        aiRadioAvailable: Boolean,
        partyAvailable: Boolean,
    ): List<CategoryState> {
        reconciled = reconcileLibraryCategories(setting)
        return visibleCategories(reconciled, aiRadioAvailable, partyAvailable)
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
