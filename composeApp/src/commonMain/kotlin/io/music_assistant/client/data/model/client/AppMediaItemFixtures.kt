package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
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
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
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

    fun artist(name: String = "Artist ${uniqueIdGenerator.nextInt()}"): Artist {
        return Artist(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
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
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            images = emptyMap(),
            duration = 210.0,
            isPlayable =  true,
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

    fun playlist(name: String = "Playlist ${uniqueIdGenerator.nextInt()}"): Playlist {
        return Playlist(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            isEditable = false,
            isDynamic = false,
            images = emptyMap(),
        )
    }

    fun podcast(name: String = "Podcast ${uniqueIdGenerator.nextInt()}"): Podcast {
        return Podcast(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
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
            PodcastEpisode(
                itemId = "blah",
                provider = "blah",
                name = it,
                providerMappings = emptyList(),
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
        name: String = "Audiobook ${uniqueIdGenerator.nextInt()}",
        chapters: List<String> = emptyList(),
    ): Audiobook {
        return Audiobook(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
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
}
