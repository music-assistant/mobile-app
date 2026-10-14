package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.QueueOverlay
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Pins the mapping of the queue's ambient sound overlay and of the sound effects that feed it.
 * A missing `overlay_enabled` is the feature gate: an older server never sends it.
 */
class QueueOverlayMappingTest {
    private val mediaItemFactory = MediaItemFactory(StubServiceClient())
    private val queueFactory = QueueFactory(mediaItemFactory)

    private val rainJson = """
        {
          "item_id": "rain",
          "provider": "ambient_sounds",
          "name": "Rain",
          "media_type": "sound_effect",
          "uri": "ambient_sounds://sound_effect/rain"
        }
    """.trimIndent()

    private fun queue(json: String) = queueFactory.create(myJson.decodeFromString<ServerQueue>(json))

    @Test
    fun `an active overlay maps its sound and volume`() {
        val info = queue(
            """{"queue_id":"q1","overlay_enabled":true,"overlay_source":$rainJson,"overlay_volume":80}""",
        )

        assertEquals(
            QueueOverlay(
                enabled = true,
                sourceUri = "ambient_sounds://sound_effect/rain",
                sourceName = "Rain",
                volume = 80,
            ),
            info.overlay,
        )
    }

    @Test
    fun `a disabled overlay keeps the last sound`() {
        val overlay = queue(
            """{"queue_id":"q1","overlay_enabled":false,"overlay_source":$rainJson,"overlay_volume":40}""",
        ).overlay

        assertEquals(false, overlay?.enabled)
        assertEquals("Rain", overlay?.sourceName)
    }

    @Test
    fun `a missing volume falls back to the server default`() {
        val overlay = queue("""{"queue_id":"q1","overlay_enabled":false}""").overlay

        assertEquals(QueueOverlay.DEFAULT_VOLUME, overlay?.volume)
        assertNull(overlay?.sourceUri)
    }

    @Test
    fun `a server without overlay support hides the feature`() {
        assertNull(queue("""{"queue_id":"q1"}""").overlay)
    }

    @Test
    fun `a sound effect keeps the uri that the overlay selects it by`() {
        val effect = assertIs<SoundEffect>(
            mediaItemFactory.create(myJson.decodeFromString<ServerMediaItem>(rainJson)),
        )

        assertEquals("ambient_sounds://sound_effect/rain", effect.uri)
    }
}
