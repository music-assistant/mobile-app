package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaItemChapter

object AppMediaItemFixtures {
    fun album(name: String, artist: String): AppMediaItem.Album {
        return AppMediaItem.Album(
            itemId = "blah",
            provider = "blah",
            name = name,
            providerMappings = emptyList(),
            metadata = null,
            favorite = null,
            uri = null,
            image = null,
            artists = listOf(artist(artist))
        )
    }

    fun artist(name: String): AppMediaItem.Artist {
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

    fun tracks(tracks: List<String>): List<AppMediaItem.Track> {
        return tracks.map {
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
                artists = null,
                album = null,
            )
        }
    }

    fun playlist(name: String): AppMediaItem.Playlist {
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

    fun podcast(name: String): AppMediaItem.Podcast {
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

    fun episodes(episodes: List<String>): List<AppMediaItem.PodcastEpisode> {
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
                podcast = null,
                fullyPlayed = null,
                resumePositionMs = null
            )
        }
    }

    fun audiobook(name: String, chapters: List<String>): AppMediaItem.Audiobook {
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
