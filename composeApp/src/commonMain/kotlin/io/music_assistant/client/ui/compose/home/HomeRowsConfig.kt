package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.settings.SettingsRepository.HomeRowPref

/** A row that can be reconciled against stored [HomeRowPref]s by stable id. */
internal interface IdProvider {
    val id: String
}

/**
 * Reconciles the live server rows against the stored [config] into an
 * ordered, enabled-flagged working list.
 *
 * Rules:
 * - A row's enabled state is taken from [config] by [IdProvider.id]; rows new
 *   to the client (absent from config) default to enabled (visible).
 * - Enabled rows come first, then disabled rows — the enabled block stays
 *   contiguous at the top so the reorderable drag constraint (index < enabledCount)
 *   holds.
 * - Within each group, rows are ordered by their index in [config]; rows absent
 *   from config keep server order and sort after the known ones (stable sort).
 * - Stored ids no longer present on the server are ignored.
 */
internal fun <T : IdProvider> reconcileHomeRows(
    rows: List<T>,
    config: List<HomeRowPref>,
    onTop: String? = null,
): List<Pair<T, Boolean>> {
    val enabledById = config.associate { it.id to it.enabled }
    val orderById = config.withIndex().associate { (index, pref) -> pref.id to index }
    val sortedRows = rows
        .map { row -> row to (enabledById[row.id] ?: true) }
        .sortedWith(
            compareByDescending<Pair<T, Boolean>> { it.second }
                .thenBy { orderById[it.first.id] ?: Int.MAX_VALUE },
        )

    return if (onTop != null && config.any { it.id == onTop }) {
        sortedRows
    } else {
        sortedRows.filter { it.first.id == onTop } +
                sortedRows.filter { it.first.id != onTop }
    }
}
