package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins crossfade decoding, and above all that an absent key stays null.
 *
 * Null is the feature gate: it hides the badge and the menu entry on a server that
 * predates crossfade. If the field ever gained a non-null default, both would appear
 * against servers that cannot honour the command — the same class of silent breakage
 * that killed the radio indicator.
 */
class ServerQueueCrossfadeTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun decode(json: String) = myJson.decodeFromString<ServerQueue>(json)

    /** The client-facing flag the badge and the overflow menu both read. */
    private fun crossfadeEnabled(json: String) = queueFactory.create(decode(json)).crossfadeEnabled

    @Test
    fun enabledIsRead() {
        assertEquals(true, crossfadeEnabled("""{"queue_id": "q1", "crossfade_enabled": true}"""))
    }

    @Test
    fun disabledIsReadAsFalseNotNull() {
        // False must survive as false: null would hide the badge instead of showing it off.
        assertEquals(false, crossfadeEnabled("""{"queue_id": "q1", "crossfade_enabled": false}"""))
    }

    @Test
    fun absentKeyMeansNoCrossfadeSupport() {
        assertNull(crossfadeEnabled("""{"queue_id": "q1"}"""))
    }

    @Test
    fun unrelatedQueueFieldsDoNotLeakIntoCrossfade() {
        // A queue that reports autoplay but not crossfade must still gate crossfade off.
        assertNull(crossfadeEnabled("""{"queue_id": "q1", "autoplay_enabled": true}"""))
    }
}
