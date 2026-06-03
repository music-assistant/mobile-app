package io.music_assistant.client.support

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.UniqueIdGenerator

object ServerMediaItemFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun album(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Album $itemId",
        artist: ServerMediaItem = artist(),
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.ALBUM.serverValue,
            artists = listOf(artist),
            uri = "http://example.com/album/$itemId",
            isPlayable = true,
        )
    }

    fun artist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Artist $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.ARTIST.serverValue,
        )
    }

    fun track(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Track $itemId",
        album: ServerMediaItem? = album(),
        artists: List<ServerMediaItem>? = album?.artists ?: listOf(artist()),
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.TRACK.serverValue,
            artists = artists,
            album = album,
            uri = "http://example.com/track/$itemId",
            isPlayable = true,
        )
    }

    fun playlist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Playlist $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.PLAYLIST.serverValue,
            isPlayable = true,
        )
    }

    fun audiobook(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Audiobook $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.AUDIOBOOK.serverValue,
            isPlayable = true,
        )
    }

    fun podcast(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Podcast $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.PODCAST.serverValue,
            isPlayable = true,
        )
    }

    fun radio(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Radio $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.RADIO.serverValue,
            isPlayable = true,
        )
    }

    fun genre(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Genre $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = "blah",
            name = name,
            mediaType = MediaType.GENRE.serverValue,
        )
    }
}
