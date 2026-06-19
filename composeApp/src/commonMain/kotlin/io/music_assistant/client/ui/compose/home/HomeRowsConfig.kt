package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.settings.SettingsRepository.HomeRowPref
import io.music_assistant.client.ui.compose.common.items.ItemCategory

/**
 * Reconciles the live server rows against the stored [config] into an
 * ordered, enabled-flagged working list.
 *
 * Rules:
 * - A row's enabled state is taken from [config] by `itemId`; rows new to the
 *   client (absent from config) default to enabled (visible).
 * - Enabled rows come first, then disabled rows — the enabled block stays
 *   contiguous at the top so the reorderable drag constraint (index < enabledCount)
 *   holds.
 * - Within each group, rows are ordered by their index in [config]; rows absent
 *   from config keep server order and sort after the known ones (stable sort).
 * - Stored ids no longer present on the server are ignored.
 */
internal fun reconcileHomeRows(
    categories: List<ItemCategory>,
    config: List<HomeRowPref>,
    onTop: String? = null,
): List<Pair<ItemCategory, Boolean>> {
    val enabledById = config.associate { it.id to it.enabled }
    val orderById = config.withIndex().associate { (index, pref) -> pref.id to index }
    val sortedCategories = categories
        .map { category -> category to (enabledById[category.id] ?: true) }
        .sortedWith(
            compareByDescending<Pair<ItemCategory, Boolean>> { it.second }
                .thenBy { orderById[it.first.id] ?: Int.MAX_VALUE },
        )

    return if (onTop != null && config.any { it.id == onTop }) {
        sortedCategories
    } else {
        sortedCategories.filter { it.first.id == onTop } +
                sortedCategories.filter { it.first.id != onTop }
    }
}
