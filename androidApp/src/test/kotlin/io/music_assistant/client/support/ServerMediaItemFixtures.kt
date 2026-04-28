package io.music_assistant.client.support

import io.music_assistant.client.data.model.server.MediaType
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
            mediaType = MediaType.ALBUM,
            artists = listOf(artist),
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
            mediaType = MediaType.ARTIST,
        )
    }
}
