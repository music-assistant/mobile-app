package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository

/**
 * Reconcile a stored library-tab config against the live [libraryCategories] universe: drop
 * unknown names (a category removed from the app) and append any categories missing from the
 * stored config (a category added after the user's config was saved, e.g. BROWSE) at the end,
 * enabled.
 *
 * Null stored config (never customized) yields every category, enabled, in declaration order.
 * Mirrors `CarActionsViewModel.reconcileTabs`.
 */
internal fun reconcileLibraryCategories(
    stored: List<SettingsRepository.LibraryCategoryPref>?,
): List<Pair<LibraryCategory, Boolean>> {
    if (stored == null) return libraryCategories.map { it to true }
    val parsed = stored.mapNotNull { pref ->
        runCatching { LibraryCategory.valueOf(pref.name) }.getOrNull()
            ?.takeIf { it in libraryCategories }
            ?.let { it to pref.enabled }
    }
    val present = parsed.map { it.first }.toSet()
    val missing = libraryCategories.filter { it !in present }.map { it to true }
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
 * Filtering for display only — the stored config keeps its entry, see [applySidebarEdit]
 * and [mergeHiddenCategories].
 */
fun visibleCategories(
    reconciled: List<Pair<LibraryCategory, Boolean>>,
    aiRadioAvailable: Boolean,
): List<Pair<LibraryCategory, Boolean>> =
    reconciled.filter { (category, _) -> category != LibraryCategory.AI_RADIO || aiRadioAvailable }

/**
 * Re-append the categories [visibleCategories] hid, keeping their stored enabled flag. For the
 * car tab editor, which reorders.
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

/**
 * Apply the enabled set and the order that the sidebar editor saved. The editor lists only the
 * [visible] categories, so a category that [visibleCategories] hid keeps its stored flag and its
 * slot: an edit while the `ai_radio` plugin is not reported yet must not store AI Radio as disabled.
 * The visible categories fill the other slots in [orderedNames] order. A visible category that
 * [orderedNames] does not name keeps its relative order after the named ones.
 */
fun applySidebarEdit(
    reconciled: List<Pair<LibraryCategory, Boolean>>,
    visible: Set<LibraryCategory>,
    enabledNames: Collection<String>,
    orderedNames: List<String>,
): List<Pair<LibraryCategory, Boolean>> {
    val named = orderedNames.mapNotNull { name -> visible.firstOrNull { it.name == name } }.distinct()
    val order = (named + reconciled.map { it.first }.filter { it in visible && it !in named }).iterator()
    return reconciled.map { (category, enabled) ->
        if (category in visible) {
            order.next().let { it to (it.name in enabledNames) }
        } else {
            category to enabled
        }
    }
}
