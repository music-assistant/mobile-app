package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.mergeHiddenCategories
import io.music_assistant.client.ui.compose.library.reconcileCarTabs
import io.music_assistant.client.ui.compose.library.visibleCategories
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Backs Settings → Car: per-kind enqueue action, per-kind bulk lists, and the Auto tabs config. */
class CarActionsViewModel(
    private val settingsRepository: SettingsRepository,
    private val dataSource: MainDataSource,
) : ViewModel() {
    /** Last reconciled config including the hidden categories, so [saveTabs] can merge them back. */
    private var reconciled: List<Pair<LibraryCategory, Boolean>> = emptyList()

    val playableClickActions = settingsRepository.carPlayableClickActions
    val browsableBulkActions = settingsRepository.carBrowsableBulkActions

    fun savePlayableClickAction(kind: ItemKind, action: DefaultClickOption) =
        settingsRepository.setCarPlayableClickAction(kind, action)

    fun saveBrowsableBulkActions(kind: ItemKind, actions: List<DefaultClickOption>) =
        settingsRepository.setCarBrowsableBulkActions(kind, actions)

    // Auto tabs reconciled against the AA-supported universe (mirrors LibraryCategoriesViewModel).
    val tabsConfig: StateFlow<List<Pair<LibraryCategory, Boolean>>> =
        combine(
            settingsRepository.carTabsConfig,
            dataSource.aiRadioAvailable,
        ) { stored, aiRadioAvailable -> visibleTabs(stored, aiRadioAvailable) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                visibleTabs(
                    settingsRepository.carTabsConfig.value,
                    dataSource.aiRadioAvailable.value,
                ),
            )

    fun saveTabs(config: List<Pair<LibraryCategory, Boolean>>) =
        settingsRepository.setCarTabsConfig(
            mergeHiddenCategories(config, reconciled).map { (cat, enabled) ->
                SettingsRepository.LibraryCategoryPref(cat.name, enabled)
            },
        )

    private fun visibleTabs(
        stored: List<SettingsRepository.LibraryCategoryPref>?,
        aiRadioAvailable: Boolean,
    ): List<Pair<LibraryCategory, Boolean>> {
        reconciled = reconcileCarTabs(stored)
        return visibleCategories(reconciled, aiRadioAvailable)
    }
}
