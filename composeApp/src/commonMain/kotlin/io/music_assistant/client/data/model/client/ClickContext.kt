package io.music_assistant.client.data.model.client

/**
 * Screen a playable item is clicked in. Keys the per-context default click action
 * (see DefaultClickAction). Only screens listed here are customizable; everywhere
 * else falls back to PLAY_NOW.
 */
enum class ClickContext { HOME, LIBRARY, BROWSE, ALBUM, PLAYLIST, ARTIST, SEARCH, DETAIL }

/** Detail-screen tabs already carry a SubItemContext; map it to a customizable context. */
fun SubItemContext.toClickContext(): ClickContext? = when (this) {
    SubItemContext.ALBUM_TRACKS -> ClickContext.ALBUM
    SubItemContext.PLAYLIST_ITEMS -> ClickContext.PLAYLIST
    SubItemContext.ARTIST_TRACKS -> ClickContext.ARTIST
    SubItemContext.ARTIST_ALBUMS, SubItemContext.PODCAST_EPISODES -> null
}
