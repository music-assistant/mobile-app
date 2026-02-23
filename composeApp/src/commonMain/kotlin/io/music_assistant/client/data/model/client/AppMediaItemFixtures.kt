package io.music_assistant.client.data.model.client

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

    private fun artist(name: String): AppMediaItem.Artist {
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
}
