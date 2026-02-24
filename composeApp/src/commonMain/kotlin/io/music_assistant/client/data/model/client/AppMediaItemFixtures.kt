package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaItemChapter
import io.music_assistant.client.utils.UniqueIdGenerator

object AppMediaItemFixtures {

    private val uniqueIdGenerator = UniqueIdGenerator()

    fun album(
        name: String = "Album ${uniqueIdGenerator.nextInt()}",
        artist: AppMediaItem.Artist = artist()
    ): AppMediaItem.Album {
        return AppMediaItem.Album(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            image = null,
            artists = listOf(artist)
        )
    }

    fun artist(name: String = "Artist ${uniqueIdGenerator.nextInt()}"): AppMediaItem.Artist {
        return AppMediaItem.Artist(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            image = null
        )
    }

    fun tracks(
        tracks: List<String>,
        album: AppMediaItem.Album? = null
    ): List<AppMediaItem.Track> {
        return tracks.map {
            val trackAlbum = album ?: album()
            val trackArtists = album?.artists ?: listOf(artist())

            AppMediaItem.Track(
                itemId = "blah",
                provider = "blah",
                name = it,
                providerMappings = emptyList(),
                metadata = null,
                favorite = null,
                uri = null,
                image = null,
                duration = null,
                artists = trackArtists,
                album = trackAlbum,
            )
        }
    }

    fun playlist(name: String = "Playlist ${uniqueIdGenerator.nextInt()}"): AppMediaItem.Playlist {
        return AppMediaItem.Playlist(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            isEditable = null,
            image = null
        )
    }

    fun podcast(name: String = "Podcast ${uniqueIdGenerator.nextInt()}"): AppMediaItem.Podcast {
        return AppMediaItem.Podcast(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            image = null
        )
    }

    fun episodes(
        episodes: List<String>,
        podcast: AppMediaItem.Podcast = podcast()
    ): List<AppMediaItem.PodcastEpisode> {
        return episodes.map {
            AppMediaItem.PodcastEpisode(
                itemId = "blah",
                provider = "blah",
                name = it,
                providerMappings = emptyList(),
                metadata = null,
                favorite = null,
                uri = null,
                image = null,
                duration = null,
                podcast = podcast,
                fullyPlayed = null,
                resumePositionMs = null
            )
        }
    }

    fun audiobook(
        name: String = "Audiobook ${uniqueIdGenerator.nextInt()}",
        chapters: List<String> = emptyList()
    ): AppMediaItem.Audiobook {
        return AppMediaItem.Audiobook(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            image = null,
            duration = null,
            authors = null,
            narrators = null,
            chapters = chapters(chapters),
            fullyPlayed = null,
            resumePositionMs = null
        )
    }

    private fun chapters(chapters: List<String>): List<MediaItemChapter> {
        return chapters.mapIndexed { index, chapter ->
            MediaItemChapter(
                position = index,
                chapter,
                start = index.toDouble(),
                end = (index + 1).toDouble()
            )
        }
    }
}
