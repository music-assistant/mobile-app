package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.utils.UniqueIdGenerator

object AppMediaItemFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun album(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Album $itemId",
        artist: Artist? = artist(),
        version: String? = null,
    ): Album {
        return Album(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = listOf(
                ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
            ),
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            version = version,
            year = null,
            artists = if (artist != null) {
                listOf(artist)
            } else {
                emptyList()
            },
        )
    }

    fun artist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Artist $itemId",
        providerMappings: List<ProviderMapping> = listOf(
            ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
        ),
    ): Artist {
        return Artist(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = providerMappings,
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
        )
    }

    fun track(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Track $itemId",
        artists: List<Artist> = listOf(artist()),
        album: Album? = null,
    ): Track {
        return Track(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = listOf(
                ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
            ),
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            duration = 210.0,
            isPlayable = true,
            artists = artists,
            album = album,
            discNumber = null,
            trackNumber = null,
            position = null,
            version = null,
        )
    }

    fun tracks(
        tracks: List<String>,
        album: Album? = null,
    ): List<Track> {
        return tracks.map {
            val trackAlbum = album ?: album(itemId = "blah")
            val trackArtists = album?.artists ?: listOf(artist())
            track(name = it, artists = trackArtists, album = trackAlbum)
        }
    }

    fun playlist(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Playlist $itemId",
    ): Playlist {
        return Playlist(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = listOf(
                ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
            ),
            metadata = null,
            favorite = null,
            uri = null,
            isEditable = false,
            isDynamic = false,
            images = emptyMap(),
        )
    }

    fun podcast(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Podcast $itemId",
    ): Podcast {
        return Podcast(
            itemId = "blah",
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = listOf(
                ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
            ),
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
        )
    }

    fun episodes(
        episodes: List<String>,
        podcast: Podcast = podcast(),
    ): List<PodcastEpisode> {
        return episodes.map {
            val itemId = uniqueIdGenerator.nextInt().toString()
            PodcastEpisode(
                itemId = itemId,
                provider = DEFAULT_PROVIDER_DOMAIN,
                name = it,
                providerMappings = listOf(
                    ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
                ),
                metadata = null,
                favorite = null,
                uri = null,
                images = emptyMap(),
                duration = null,
                podcast = podcast,
                fullyPlayed = null,
                resumePositionMs = null,
                version = null,
                isPlayable = true,
            )
        }
    }

    fun audiobook(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Audiobook ${uniqueIdGenerator.nextInt()}",
        chapters: List<String> = emptyList(),
    ): Audiobook {
        return Audiobook(
            itemId = itemId,
            provider = DEFAULT_PROVIDER_DOMAIN,
            name = name,
            providerMappings = listOf(
                ProviderMapping(itemId, DEFAULT_PROVIDER_DOMAIN, DEFAULT_PROVIDER_INSTANCE),
            ),
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            duration = null,
            isPlayable = true,
            authors = null,
            narrators = null,
            chapters = chapters(chapters),
            fullyPlayed = null,
            resumePositionMs = null,
            version = null,
        )
    }

    private fun chapters(chapters: List<String>): List<Chapter> {
        return chapters.mapIndexed { index, chapter ->
            Chapter(
                position = index,
                chapter,
                start = index.toDouble(),
                end = (index + 1).toDouble(),
            )
        }
    }

    private const val DEFAULT_PROVIDER_DOMAIN = "test-domain"
    private const val DEFAULT_PROVIDER_INSTANCE = "test-instance"
}
