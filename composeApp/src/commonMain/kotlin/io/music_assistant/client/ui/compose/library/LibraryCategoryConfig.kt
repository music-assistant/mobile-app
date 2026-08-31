package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository

/**
 * Reconcile a stored library-tab config against the live [LibraryCategory] universe: drop unknown
 * names (a category removed from the app) and append any categories missing from the stored config
 * (a category added after the user's config was saved, e.g. BROWSE) at the end, enabled.
 *
 * Null stored config (never customized) yields every category, enabled, in declaration order.
 * Mirrors `CarActionsViewModel.reconcileTabs`.
 */
internal fun reconcileLibraryCategories(
    stored: List<SettingsRepository.LibraryCategoryPref>?,
): List<Pair<LibraryCategory, Boolean>> {
    if (stored == null) return LibraryCategory.entries.map { it to true }
    val parsed = stored.mapNotNull { pref ->
        runCatching { LibraryCategory.valueOf(pref.name) }.getOrNull()?.let { it to pref.enabled }
    }
    val present = parsed.map { it.first }.toSet()
    val missing = LibraryCategory.entries.filter { it !in present }.map { it to true }
    return parsed + missing
}

/**
 * Reconcile a stored car-tab config against [carTabCategories], the subset Android Auto and
 * CarPlay can render at their root. Same contract as [reconcileLibraryCategories], clamped to
 * that subset.
 *
 * Public rather than internal: `AutoLibrary` lives in the `androidApp` module and needs it.
 */
fun reconcileCarTabs(
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

/**
 * Drop the categories the server cannot serve right now. [LibraryCategory.AI_RADIO] exists only
 * while the optional `ai_radio` plugin is loaded and the user holds its scope, so it is hidden
 * from the grid, from the tab editor and from both car browse trees when it is not.
 *
 * Filtering for display only — the stored config keeps its entry, see [mergeHiddenCategories].
 */
fun visibleCategories(
    reconciled: List<Pair<LibraryCategory, Boolean>>,
    aiRadioAvailable: Boolean,
): List<Pair<LibraryCategory, Boolean>> =
    reconciled.filter { (category, _) -> category != LibraryCategory.AI_RADIO || aiRadioAvailable }

/**
 * Re-append the categories [visibleCategories] hid, keeping their stored enabled flag.
 *
 * Without this, saving the tab editor while a category is hidden would drop it from the stored
 * config, and [reconcileLibraryCategories] would then re-append it *enabled* — silently
 * resurrecting a category the user had turned off.
 */
fun mergeHiddenCategories(
    newOrder: List<Pair<LibraryCategory, Boolean>>,
    reconciled: List<Pair<LibraryCategory, Boolean>>,
): List<Pair<LibraryCategory, Boolean>> {
    val visible = newOrder.map { it.first }.toSet()
    return newOrder + reconciled.filterNot { it.first in visible }
}
