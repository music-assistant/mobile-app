package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.SettingsRepository.HomeRowPref
import io.music_assistant.client.ui.compose.common.items.lazyListKey

// Public rather than internal: `AutoLibrary` lives in the `androidApp` module and mirrors the
// home rows through these rules.

/** A row that can be reconciled against stored [HomeRowPref]s by stable id. */
interface IdProvider {
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
fun <T : IdProvider> reconcileHomeRows(
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

/** Types a home row renders; anything else in a row (sound effects, nested folders) is skipped. */
fun AppMediaItem.isHomeRowItem(): Boolean =
    this is Track ||
            this is Artist ||
            this is Album ||
            this is Playlist ||
            this is Audiobook ||
            this is Podcast ||
            this is PodcastEpisode ||
            this is RadioStation ||
            this is Genre

/**
 * The resolved recommendation [folders] the home page shows, in the user's order: rows with
 * something to render, deduped, reconciled against [config], enabled only.
 */
fun visibleHomeFolders(
    folders: List<RecommendationFolder>,
    config: List<HomeRowPref>,
): List<RecommendationFolder> = reconcileHomeRows(
    rows = folders
        .filter { folder -> folder.items.orEmpty().any { it.isHomeRowItem() } }
        .distinctBy { it.lazyListKey() }
        .map(::FolderRow),
    config = config,
).mapNotNull { (row, enabled) -> row.folder.takeIf { enabled } }

private class FolderRow(val folder: RecommendationFolder) : IdProvider {
    override val id: String get() = folder.itemId
}
