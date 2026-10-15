package io.music_assistant.client.ui.compose.item.artist

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.ui.compose.item.ItemList
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistSectionOptionsTest {
    private val tidal = ProviderMapping("t1", "tidal", "tidal-1")
    private val filesystem = ProviderMapping("f1", "filesystem_local", "fs-1")

    private fun provider(
        instanceId: String,
        type: String,
        topTracks: Boolean = true,
        available: Boolean = true,
    ) = ServerProviderInstance(
        instanceId = instanceId,
        name = instanceId,
        domain = instanceId,
        type = type,
        available = available,
        supportedFeatures = if (topTracks) listOf(ARTIST_TOPTRACKS_FEATURE) else emptyList(),
    )

    private val libraryArtist = AppMediaItemFixtures.artist(itemId = "42", providerMappings = listOf(tidal, filesystem))
        .copy(provider = ServerMediaItem.LIBRARY_PROVIDER)

    @Test
    fun `library artist gets merged capable mapped providers and metadata providers`() {
        val providers = listOf(
            provider("lastfm-1", "metadata"),
            provider("tidal-1", "music"),
            provider("fs-1", "music", topTracks = false),
            provider("spotify-1", "music"),
            provider("ai-1", "plugin"),
            provider("down-1", "metadata", available = false),
        )

        assertEquals(
            listOf(
                ItemList.ArtistLibraryTopTracks("42"),
                ItemList.ArtistLibraryTopTracks("42", "tidal-1"),
                ItemList.ArtistLibraryTopTracks("42", "lastfm-1"),
                ItemList.ArtistLibraryTopTracks("42", "ai-1"),
            ),
            libraryArtist.topTracksOptions(providers),
        )
    }

    @Test
    fun `library artist without provider list gets the merged listing only`() {
        assertEquals(listOf(ItemList.ArtistLibraryTopTracks("42")), libraryArtist.topTracksOptions(emptyList()))
    }

    @Test
    fun `provider artist keeps one option per provider instance`() {
        val sameInstance = ProviderMapping("t2", "tidal", "tidal-1")
        val artist = AppMediaItemFixtures.artist(providerMappings = listOf(tidal, sameInstance, filesystem))

        assertEquals(
            listOf(ItemList.ArtistTopTracks(listOf(tidal, sameInstance)), ItemList.ArtistTopTracks(listOf(filesystem))),
            artist.topTracksOptions(listOf(provider("lastfm-1", "metadata"))),
        )
    }
}
