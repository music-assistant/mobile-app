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
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track

/**
 * The multi-select state of the track and episode rows on one screen. The row menu offers
 * "Select multiple" only where a screen provides an instance through [LocalItemSelection].
 * [items] keeps the order of selection, and the bulk actions send the items in that order.
 * [candidates] are the selectable items that the screen shows, in list order ([SelectableItems]).
 * [selectAll] selects them in that order. The mode ends when the last item is deselected, so an
 * active selection is never empty. [key] says which rows are the same item.
 */
@Stable
class ItemSelection(private val key: SelectionKey = SelectionKey.URI) {
    private val selected = mutableStateListOf<AppMediaItem>()

    /** A copy, so a caller keeps the items of the moment it read them. */
    val items: List<AppMediaItem> get() = selected.toList()

    val isActive: Boolean get() = selected.isNotEmpty()

    var candidates: List<AppMediaItem> by mutableStateOf(emptyList())
        private set

    /** True while the screen shows a selectable item that is not selected. */
    val canSelectAll: Boolean get() = candidates.any { !isSelected(it) }

    /**
     * A track or an episode that a bulk action can send: it plays, it has a uri, and it has a
     * [key]. The bulk actions apply to both types in the same way.
     */
    fun canSelect(item: AppMediaItem): Boolean =
        (item is Track || item is PodcastEpisode) &&
            (item as? PlayableItem)?.isPlayable == true && item.uri != null && key.of(item) != null

    fun isSelected(item: AppMediaItem): Boolean = selected.any { key.of(it) == key.of(item) }

    /** Starts the mode with [item] selected. */
    fun start(item: AppMediaItem) {
        selected.clear()
        if (canSelect(item)) selected.add(item)
    }

    fun toggle(item: AppMediaItem) {
        if (!canSelect(item)) return
        val index = selected.indexOfFirst { key.of(it) == key.of(item) }
        if (index >= 0) selected.removeAt(index) else selected.add(item)
    }

    /** Selects every candidate in list order, so a bulk action follows the order of the screen. */
    fun selectAll() {
        if (candidates.isEmpty()) return
        selected.clear()
        selected.addAll(candidates)
    }

    fun clear() = selected.clear()

    internal fun selectableOf(items: List<AppMediaItem>): List<AppMediaItem> =
        items.filter(::canSelect).distinctBy(key::of)

    internal fun offer(items: List<AppMediaItem>) {
        candidates = items
    }

    internal fun withdraw(items: List<AppMediaItem>) {
        if (candidates === items) candidates = emptyList()
    }
}

/**
 * Makes the selectable items of [items] the candidates of "Select all", while this call is in
 * the composition. Call it once for each screen, at a level that does not leave the composition
 * when the list scrolls.
 */
@Composable
fun SelectableItems(items: List<AppMediaItem>) {
    val selection = LocalItemSelection.current ?: return
    val selectable = remember(selection, items) { selection.selectableOf(items) }
    DisposableEffect(selection, selectable) {
        selection.offer(selectable)
        onDispose { selection.withdraw(selectable) }
    }
}

/**
 * What makes two rows the same item. A playlist can hold one item more than once, and its
 * removal needs the server position, so a playlist keys on [POSITION]. Other lists key on [URI].
 */
enum class SelectionKey {
    URI,
    POSITION,
    ;

    fun of(item: AppMediaItem): Any? = when (this) {
        URI -> item.uri
        POSITION -> (item as? PlayableItem)?.position
    }
}

val LocalItemSelection = staticCompositionLocalOf<ItemSelection?> { null }
