package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingFolderHierarchyTest {
    @Test
    fun `filesystem provider mapping supplies folder hierarchy for library track`() {
        val track = track(
            uri = "library://track/13328",
            mappingItemId = "Hungarian/Bon Bon/Album/01 Song.mp3",
        )

        assertEquals("Hungarian › Bon Bon › Album", track.nowPlayingFolderHierarchy())
    }

    @Test
    fun `normalized library uri alone does not produce a hierarchy`() {
        assertNull(track(uri = "library://track/13328").nowPlayingFolderHierarchy())
    }

    private fun track(uri: String?, mappingItemId: String? = null) =
        Track(
            itemId = "13328",
            provider = "library",
            name = "Song",
            providerMappings =
                mappingItemId?.let {
                    listOf(
                        ProviderMapping(
                            itemId = it,
                            providerDomain = "filesystem_local",
                            providerInstance = "filesystem_local--test",
                        ),
                    )
                },
            metadata = null,
            favorite = false,
            uri = uri,
            images = emptyMap(),
            duration = 180.0,
            isPlayable = true,
            artists = emptyList(),
            album = null,
            discNumber = null,
            trackNumber = null,
            position = null,
            version = null,
        )
}
