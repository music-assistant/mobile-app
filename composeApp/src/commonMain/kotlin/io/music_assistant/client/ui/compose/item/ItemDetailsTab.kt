package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track

/**
 * The tabs an item-details screen can show. [sortContext] drives the sort chip (null = no sorting);
 * [viewMediaType] drives the list/grid view-mode toggle (null = no toggle). Which tabs an item type
 * exposes is [tabsFor]; the selected tab is owned by [ItemDetailsViewModel.State].
 */
enum class ItemDetailsTab(
    val sortContext: SubItemContext?,
    val viewMediaType: MediaType?,
) {
    ALBUM_TRACKS(SubItemContext.ALBUM_TRACKS, MediaType.TRACK),
    PLAYLIST_ITEMS(SubItemContext.PLAYLIST_ITEMS, MediaType.TRACK),
    RADIO_TRACKS(SubItemContext.RADIO_TRACKS, MediaType.TRACK),
    RADIO_VERSIONS(null, MediaType.RADIO),
    PODCAST_EPISODES(SubItemContext.PODCAST_EPISODES, MediaType.TRACK),
    COLLECTION_ITEMS(SubItemContext.COLLECTION_ITEMS, MediaType.TRACK),
    AUDIOBOOK_CHAPTERS(null, null),
    GENRE_ARTISTS(null, MediaType.ARTIST),
    GENRE_ALBUMS(null, MediaType.ALBUM),
    TRACK_ALBUMS(null, MediaType.ALBUM),
    TRACK_VERSIONS(null, MediaType.TRACK),
    SIMILAR_TRACKS(null, MediaType.TRACK),
}
fun tabsFor(item: AppMediaItem): List<ItemDetailsTab> = when (item) {
    is Album -> listOf(ItemDetailsTab.ALBUM_TRACKS)
    is Playlist -> listOf(ItemDetailsTab.PLAYLIST_ITEMS)
    is RadioStation -> listOf(
        ItemDetailsTab.RADIO_TRACKS,
        ItemDetailsTab.RADIO_VERSIONS,
    )
    is Podcast -> listOf(ItemDetailsTab.PODCAST_EPISODES)
    is Audiobook -> listOf(ItemDetailsTab.AUDIOBOOK_CHAPTERS)
    is MediaCollection -> listOf(ItemDetailsTab.COLLECTION_ITEMS)
    is Genre -> listOf(ItemDetailsTab.GENRE_ARTISTS, ItemDetailsTab.GENRE_ALBUMS)
    is Track -> listOf(
        ItemDetailsTab.TRACK_ALBUMS,
        ItemDetailsTab.TRACK_VERSIONS,
        ItemDetailsTab.SIMILAR_TRACKS,
    )
    else -> emptyList()
}

/**
 * True for the item types that [ItemDetailsViewModel] loads (its `getItemById`); keep both in step.
 * Gates a navigation to `ItemDetails` from a place that can show any media type.
 */
val AppMediaItem.opensItemDetails: Boolean
    get() = mediaType in ITEM_DETAILS_TYPES

private val ITEM_DETAILS_TYPES = setOf(
    MediaType.ARTIST,
    MediaType.ALBUM,
    MediaType.PLAYLIST,
    MediaType.RADIO,
    MediaType.PODCAST,
    MediaType.AUDIOBOOK,
    MediaType.GENRE,
    MediaType.COLLECTION,
)
