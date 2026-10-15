package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.ProviderMapping
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins [io.music_assistant.client.data.model.client.items.Playlist.accepts] to the server's add rule. */
class PlaylistAcceptsTest {
    private fun playlistOn(domain: String, types: Set<MediaType>?) = testPlaylist().copy(
        providerMappings = listOf(ProviderMapping(itemId = "p", providerDomain = domain, providerInstance = domain)),
        supportedMediaTypes = types,
    )

    @Test
    fun `builtin playlist accepts every type regardless of supported types`() {
        val playlist = playlistOn("builtin", setOf(MediaType.TRACK))
        assertTrue(playlist.accepts(testRadio()))
        assertTrue(playlist.accepts(testPodcastEpisode()))
        assertTrue(playlist.accepts(testAudiobook()))
    }

    @Test
    fun `provider playlist accepts only its supported types`() {
        val playlist = playlistOn("spotify", setOf(MediaType.TRACK))
        assertTrue(playlist.accepts(testTrack()))
        assertFalse(playlist.accepts(testRadio()))
        assertFalse(playlist.accepts(testPodcastEpisode()))
    }

    @Test
    fun `album is checked as tracks because the server unwraps it`() {
        assertTrue(playlistOn("spotify", setOf(MediaType.TRACK)).accepts(testAlbum()))
        assertFalse(playlistOn("spotify", setOf(MediaType.RADIO)).accepts(testAlbum()))
    }

    @Test
    fun `unknown supported types exclude nothing`() {
        assertTrue(playlistOn("spotify", null).accepts(testRadio()))
    }
}
