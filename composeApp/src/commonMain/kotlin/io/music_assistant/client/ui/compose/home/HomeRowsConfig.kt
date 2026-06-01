package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.settings.SettingsRepository.HomeRowPref

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
    serverRows: List<RecommendationFolder>,
    config: List<HomeRowPref>,
): List<Pair<RecommendationFolder, Boolean>> {
    val enabledById = config.associate { it.id to it.enabled }
    val orderById = config.withIndex().associate { (index, pref) -> pref.id to index }
    return serverRows
        .map { row -> row to (enabledById[row.itemId] ?: true) }
        .sortedWith(
            compareByDescending<Pair<RecommendationFolder, Boolean>> { it.second }
                .thenBy { orderById[it.first.itemId] ?: Int.MAX_VALUE },
        )
}
