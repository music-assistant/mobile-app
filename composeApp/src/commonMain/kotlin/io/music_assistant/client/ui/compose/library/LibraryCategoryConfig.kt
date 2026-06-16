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
