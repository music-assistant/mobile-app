package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * The two release rows of a singer, split like the web app: singles and EPs, and all other
 * albums. An album of unknown type is in [ALBUMS].
 */
enum class ArtistReleases {
    ALBUMS,
    SINGLES_EPS,
    ;

    fun contains(item: AppMediaItem): Boolean =
        item is Album && (item.albumType == AlbumType.SINGLE || item.albumType == AlbumType.EP) == (this == SINGLES_EPS)
}
