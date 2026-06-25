package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast

/**
 * The tabs an item-details screen can show. [sortContext] drives the sort chip (null = no sorting);
 * [viewMediaType] drives the list/grid view-mode toggle (null = no toggle). Which tabs an item type
 * exposes is [tabsFor]; the selected tab is owned by [ItemDetailsViewModel.State].
 */
enum class ItemDetailsTab(
    val sortContext: SubItemContext?,
    val viewMediaType: MediaType?,
) {
    ARTIST_ALBUMS(SubItemContext.ARTIST_ALBUMS, MediaType.ALBUM),
    ARTIST_TRACKS(SubItemContext.ARTIST_TRACKS, MediaType.TRACK),
    ALBUM_TRACKS(SubItemContext.ALBUM_TRACKS, MediaType.TRACK),
    PLAYLIST_ITEMS(SubItemContext.PLAYLIST_ITEMS, MediaType.TRACK),
    PODCAST_EPISODES(SubItemContext.PODCAST_EPISODES, MediaType.TRACK),
    AUDIOBOOK_CHAPTERS(null, null),
    GENRE_ARTISTS(null, MediaType.ARTIST),
    GENRE_ALBUMS(null, MediaType.ALBUM),
}

fun tabsFor(item: AppMediaItem): List<ItemDetailsTab> = when (item) {
    is Artist -> listOf(ItemDetailsTab.ARTIST_ALBUMS, ItemDetailsTab.ARTIST_TRACKS)
    is Album -> listOf(ItemDetailsTab.ALBUM_TRACKS)
    is Playlist -> listOf(ItemDetailsTab.PLAYLIST_ITEMS)
    is Podcast -> listOf(ItemDetailsTab.PODCAST_EPISODES)
    is Audiobook -> listOf(ItemDetailsTab.AUDIOBOOK_CHAPTERS)
    is Genre -> listOf(ItemDetailsTab.GENRE_ARTISTS, ItemDetailsTab.GENRE_ALBUMS)
    else -> emptyList()
}
