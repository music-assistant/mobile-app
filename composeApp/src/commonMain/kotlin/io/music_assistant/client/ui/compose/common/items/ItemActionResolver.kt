package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track

/** Item types accepted by playlist edits (server contract, not a UI choice). */
val AppMediaItem.supportsAddToPlaylist: Boolean
    get() = this is Track || this is Album || this is RadioStation ||
            this is PodcastEpisode || this is Audiobook

/**
 * Long-click menu shows the union: playback block (Play Now / Insert Next & Play / Insert Next /
 * Add to Bottom / Start Radio) followed by library, favorite, playlist and progress entries.
 */
fun resolveLongClickActions(
    item: AppMediaItem,
    librarySupported: Boolean,
    canAddToPlaylist: Boolean,
    canRemoveFromPlaylist: Boolean,
    progressSupported: Boolean,
    defaultAction: ItemAction? = null,
): List<ItemAction> = buildList {
    if (item.isPlayable) addPlaybackActions(item)
    if (librarySupported) {
        add(if (item.isInLibrary) ItemAction.RemoveFromLibrary else ItemAction.AddToLibrary)
        if (item.isInLibrary) {
            add(if (item.favorite == true) ItemAction.Unfavorite else ItemAction.Favorite)
        }
    }
    if (canAddToPlaylist) add(ItemAction.AddToPlaylist)
    if (canRemoveFromPlaylist) add(ItemAction.RemoveFromPlaylist)
    if (progressSupported && item.isFullyPlayed()) {
        add(ItemAction.MarkUnplayed)
    } else if (progressSupported) {
        add(ItemAction.MarkPlayed)
    }
    // The action a click performs is hoisted to the top (and labeled "Default" by the menu).
}.let { actions ->
    if (defaultAction == null) {
        actions
    } else {
        listOf(defaultAction) + actions.filterNot { it == defaultAction }
    }
}

/**
 * Item detail screen play-button split-button overflow — every applicable play action
 * except [default] (which the leading button performs).
 */
fun resolvePlayButtonActions(item: AppMediaItem, default: ItemAction?): List<ItemAction> = buildList {
    if (item.isPlayable) addPlaybackActions(item)
}.filterNot { it == default }

/** The playback block: Play Now / Insert Next & Play / Insert Next / Add to Bottom / Start Radio. */
private fun MutableList<ItemAction>.addPlaybackActions(item: AppMediaItem) {
    add(ItemAction.Play(QueueOption.REPLACE))
    add(ItemAction.Play(QueueOption.PLAY))
    add(ItemAction.Play(QueueOption.NEXT))
    add(ItemAction.Play(QueueOption.ADD))
    if (item.canStartRadio) add(ItemAction.StartRadio)
}

/**
 * Item detail screen three-dot overflow — library/favorite/playlist. Navigation entries
 * (Go to album / artist) are appended separately at the call site via [navigationOptions].
 */
fun resolveDetailOverflowActions(
    item: AppMediaItem,
    librarySupported: Boolean,
    canAddToPlaylist: Boolean,
): List<ItemAction> = buildList {
    if (librarySupported) {
        add(if (item.isInLibrary) ItemAction.RemoveFromLibrary else ItemAction.AddToLibrary)
        if (item.isInLibrary) {
            add(if (item.favorite == true) ItemAction.Unfavorite else ItemAction.Favorite)
        }
    }
    if (canAddToPlaylist) add(ItemAction.AddToPlaylist)
}

private fun AppMediaItem.isFullyPlayed(): Boolean = when (this) {
    is PodcastEpisode -> fullyPlayed == true
    is Audiobook -> fullyPlayed == true
    else -> false
}
