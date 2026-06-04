package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind

/**
 * User-chosen action a single click (or detail-page play button) performs, keyed by
 * (ItemKind, ClickContext). Pure preference value (no UI deps) — UI mapping lives in
 * DefaultClickActionUi.kt.
 */
enum class DefaultClickAction {
    PLAY_NOW,
    INSERT_NEXT_AND_PLAY,
    INSERT_NEXT,
    ADD_TO_QUEUE,
    START_RADIO,
    ;

    /** Whether this action is ever meaningful for [kind] — gates the matrix ROW. */
    fun appliesTo(kind: ItemKind): Boolean = when (this) {
        START_RADIO -> kind in setOf(
            ItemKind.TRACK,
            ItemKind.ALBUM,
            ItemKind.ARTIST,
            ItemKind.PLAYLIST,
        )

        else -> true
    }

    /**
     * Whether this action is selectable in [context] for [kind] — gates the matrix CELL.
     * Extension hook for context-restricted actions (e.g. a future Track action valid
     * only in Album/Playlist); currently everything applicable is available everywhere.
     */
    fun isAvailableIn(context: ClickContext, kind: ItemKind): Boolean = when (context) {
        ClickContext.ARTIST,
        ClickContext.PLAYLIST,
        -> appliesTo(kind) // TODO wire up logic for start from here
        else -> appliesTo(kind)
    }
}
