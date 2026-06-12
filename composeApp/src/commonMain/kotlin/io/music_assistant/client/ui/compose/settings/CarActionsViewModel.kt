package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.carTabCategories
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Backs Settings → Car: per-kind enqueue action, per-kind bulk lists, and the Auto tabs config. */
class CarActionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val playableClickActions = settingsRepository.carPlayableClickActions
    val browsableBulkActions = settingsRepository.carBrowsableBulkActions

    fun savePlayableClickAction(kind: ItemKind, action: DefaultClickOption) =
        settingsRepository.setCarPlayableClickAction(kind, action)

    fun saveBrowsableBulkActions(kind: ItemKind, actions: List<DefaultClickOption>) =
        settingsRepository.setCarBrowsableBulkActions(kind, actions)

    // Auto tabs reconciled against the AA-supported universe (mirrors LibraryCategoriesViewModel).
    val tabsConfig: StateFlow<List<Pair<LibraryCategory, Boolean>>> =
        settingsRepository.carTabsConfig
            .map { reconcileTabs(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                reconcileTabs(settingsRepository.carTabsConfig.value),
            )

    fun saveTabs(config: List<Pair<LibraryCategory, Boolean>>) =
        settingsRepository.setCarTabsConfig(
            config.map { (cat, enabled) -> SettingsRepository.LibraryCategoryPref(cat.name, enabled) },
        )

    private fun reconcileTabs(
        stored: List<SettingsRepository.LibraryCategoryPref>?,
    ): List<Pair<LibraryCategory, Boolean>> {
        if (stored == null) return carTabCategories.map { it to true }
        val parsed = stored.mapNotNull { pref ->
            runCatching { LibraryCategory.valueOf(pref.name) }.getOrNull()
                ?.takeIf { it in carTabCategories }
                ?.let { it to pref.enabled }
        }
        val present = parsed.map { it.first }.toSet()
        val missing = carTabCategories.filter { it !in present }.map { it to true }
        return parsed + missing
    }
}
