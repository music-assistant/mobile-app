package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaveformKeyTest {
    @Test
    fun `a track with a started stream has the key of the stream`() {
        val queueTrack = AppMediaItemFixtures.track().toQueueTrack()
            .copy(provider = "spotify--abc", streamItemId = "stream-1")

        assertEquals(WaveformKey(itemId = "stream-1", provider = "spotify--abc"), queueTrack.waveformKey)
    }

    @Test
    fun `a track before its stream starts has no key`() {
        val queueTrack = AppMediaItemFixtures.track().toQueueTrack().copy(provider = "spotify--abc")

        assertNull(queueTrack.waveformKey)
    }

    @Test
    fun `an item that is not a track has no key`() {
        val queueTrack = AppMediaItemFixtures.audiobook().toQueueTrack()
            .copy(provider = "audiobookshelf--abc", streamItemId = "book-1")

        assertNull(queueTrack.waveformKey)
    }
}
