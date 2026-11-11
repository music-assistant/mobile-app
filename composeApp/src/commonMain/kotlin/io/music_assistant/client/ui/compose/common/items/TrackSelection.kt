package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Track

/**
 * The multi-select state of the track rows on one screen. The track menu offers
 * "Select multiple" only where a screen provides an instance through [LocalTrackSelection].
 * [tracks] keeps the order of selection, and the bulk actions send the tracks in that order.
 * [candidates] are the selectable tracks that the screen shows, in list order ([SelectableTracks]).
 * [selectAll] selects them in that order. The mode ends when the last track is deselected, so an
 * active selection is never empty. [key] says which tracks are the same track.
 */
@Stable
class TrackSelection(private val key: SelectionKey = SelectionKey.URI) {
    private val selected = mutableStateListOf<Track>()

    val tracks: List<Track> get() = selected

    val isActive: Boolean get() = selected.isNotEmpty()

    var candidates: List<Track> by mutableStateOf(emptyList())
        private set

    /** True while the screen shows a selectable track that is not selected. */
    val canSelectAll: Boolean get() = candidates.any { !isSelected(it) }

    /** A track that a bulk action can send: it plays, it has a uri, and it has a [key]. */
    fun canSelect(track: Track): Boolean = track.isPlayable && track.uri != null && key.of(track) != null

    fun isSelected(track: Track): Boolean = selected.any { key.of(it) == key.of(track) }

    /** Starts the mode with [track] selected. */
    fun start(track: Track) {
        selected.clear()
        if (canSelect(track)) selected.add(track)
    }

    fun toggle(track: Track) {
        if (!canSelect(track)) return
        val index = selected.indexOfFirst { key.of(it) == key.of(track) }
        if (index >= 0) selected.removeAt(index) else selected.add(track)
    }

    internal fun selectableOf(items: List<AppMediaItem>): List<Track> =
        items.filterIsInstance<Track>().filter(::canSelect).distinctBy(key::of)

    /** Selects every candidate in list order, so a bulk action follows the order of the screen. */
    fun selectAll() {
        if (candidates.isEmpty()) return
        selected.clear()
        selected.addAll(candidates)
    }

    fun clear() = selected.clear()

    internal fun offer(tracks: List<Track>) {
        candidates = tracks
    }

    internal fun withdraw(tracks: List<Track>) {
        if (candidates === tracks) candidates = emptyList()
    }
}

/**
 * Makes the selectable tracks of [items] the candidates of "Select all", while this call is in
 * the composition. Call it once for each screen, at a level that does not leave the composition
 * when the list scrolls.
 */
@Composable
fun SelectableTracks(items: List<AppMediaItem>) {
    val selection = LocalTrackSelection.current ?: return
    val tracks = remember(selection, items) { selection.selectableOf(items) }
    DisposableEffect(selection, tracks) {
        selection.offer(tracks)
        onDispose { selection.withdraw(tracks) }
    }
}

/**
 * What makes two rows the same track. A playlist can hold one track more than once, and its
 * removal needs the server position, so a playlist keys on [POSITION]. Other lists key on [URI].
 */
enum class SelectionKey {
    URI,
    POSITION,
    ;

    fun of(track: Track): Any? = when (this) {
        URI -> track.uri
        POSITION -> track.position
    }
}

val LocalTrackSelection = staticCompositionLocalOf<TrackSelection?> { null }
