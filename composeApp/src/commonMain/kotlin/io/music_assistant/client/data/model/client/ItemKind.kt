package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track

/**
 * Item types that get an independent default-action table. Playable kinds
 * (track/radio/podcast-episode) are customizable per list context; browsable kinds
 * (album/artist/…) are customizable only on their detail-page play button. Used as a
 * preference key together with ClickContext.
 */
enum class ItemKind { TRACK, RADIO, PODCAST_EPISODE, ALBUM, ARTIST, PLAYLIST, PODCAST, AUDIOBOOK }

fun AppMediaItem.itemKind(): ItemKind? = when (this) {
    is Track -> ItemKind.TRACK
    is RadioStation -> ItemKind.RADIO
    is PodcastEpisode -> ItemKind.PODCAST_EPISODE
    is Album -> ItemKind.ALBUM
    is Artist -> ItemKind.ARTIST
    is Playlist -> ItemKind.PLAYLIST
    is Podcast -> ItemKind.PODCAST
    is Audiobook -> ItemKind.AUDIOBOOK
    else -> null // Genre etc. — not customizable
}

/** Which context columns this kind shows in the customize dialog. */
fun ItemKind.appearsIn(context: ClickContext): Boolean = when (this) {
    ItemKind.TRACK -> context != ClickContext.DETAIL
    ItemKind.RADIO -> context in setOf(ClickContext.HOME, ClickContext.LIBRARY, ClickContext.SEARCH)
    ItemKind.PODCAST_EPISODE -> context in setOf(ClickContext.HOME, ClickContext.SEARCH)
    ItemKind.ALBUM,
    ItemKind.ARTIST,
    ItemKind.PLAYLIST,
    ItemKind.PODCAST,
    ItemKind.AUDIOBOOK,
    -> context == ClickContext.DETAIL
}
