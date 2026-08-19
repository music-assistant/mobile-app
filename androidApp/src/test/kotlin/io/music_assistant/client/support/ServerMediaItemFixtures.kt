package io.music_assistant.client.support

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.UniqueIdGenerator

object ServerMediaItemFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun album(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Album $itemId",
        artist: ServerMediaItem = artist(),
        favorite: Boolean? = null,
        provider: Provider = Provider(DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = provider.domain,
            name = name,
            mediaType = MediaType.ALBUM.serverValue,
            artists = listOf(artist),
            uri = "http://example.com/album/$itemId",
            isPlayable = true,
            favorite = favorite,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = provider.domain,
                    providerInstance = provider.instance,
                ),
            ),
        )
    }

    fun artist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Artist $itemId",
        provider: Provider = Provider(DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = provider.domain,
            name = name,
            mediaType = MediaType.ARTIST.serverValue,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = provider.domain,
                    providerInstance = provider.instance,
                ),
            ),
        )
    }

    fun track(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Track $itemId",
        album: ServerMediaItem? = album(),
        artists: List<ServerMediaItem>? = album?.artists ?: listOf(artist()),
        provider: Provider = Provider(DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.TRACK.serverValue,
            artists = artists,
            album = album,
            uri = "http://example.com/track/$itemId",
            isPlayable = true,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = provider.domain,
                    providerInstance = provider.instance,
                ),
            ),
        )
    }

    fun playlist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Playlist $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.PLAYLIST.serverValue,
            isPlayable = true,
            uri = "http://example.com/playlist/$itemId",
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = DEFAULT_PROVIDER_DOMAIN,
                    providerInstance = DEFAULT_PROVIDER_INSTANCE,
                ),
            ),
        )
    }

    fun audiobook(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Audiobook $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.AUDIOBOOK.serverValue,
            isPlayable = true,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = DEFAULT_PROVIDER_DOMAIN,
                    providerInstance = DEFAULT_PROVIDER_INSTANCE,
                ),
            ),
        )
    }

    fun podcast(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Podcast $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.PODCAST.serverValue,
            isPlayable = true,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = DEFAULT_PROVIDER_DOMAIN,
                    providerInstance = DEFAULT_PROVIDER_INSTANCE,
                ),
            ),
        )
    }

    fun radio(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Radio $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.RADIO.serverValue,
            isPlayable = true,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = DEFAULT_PROVIDER_DOMAIN,
                    providerInstance = DEFAULT_PROVIDER_INSTANCE,
                ),
            ),
        )
    }

    fun genre(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Genre $itemId",
    ): ServerMediaItem {
        return ServerMediaItem(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            mediaType = MediaType.GENRE.serverValue,
            providerMappings = listOf(
                ProviderMapping(
                    itemId = itemId,
                    providerDomain = DEFAULT_PROVIDER_DOMAIN,
                    providerInstance = DEFAULT_PROVIDER_INSTANCE,
                ),
            ),
        )
    }

    fun provider(
        domain: String = DEFAULT_PROVIDER_DOMAIN,
        instance: String = DEFAULT_PROVIDER_INSTANCE,
    ): Provider {
        return Provider(domain, instance)
    }

    private const val DEFAULT_PROVIDER_DOMAIN = "test-domain"
    private const val DEFAULT_PROVIDER_INSTANCE = "test-instance"

    data class Provider(val domain: String, val instance: String)
}
