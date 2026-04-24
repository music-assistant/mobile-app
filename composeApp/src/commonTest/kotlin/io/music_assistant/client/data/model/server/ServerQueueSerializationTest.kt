package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the tolerance contract of [ServerQueue] deserialization: only
 * `queue_id` is required, every other field carries a default, and an
 * unknown [RepeatMode] variant coerces to [RepeatMode.OFF] via
 * `coerceInputValues = true` on [myJson]. The upshot is that one stale
 * entry in a `List<ServerQueue>` payload never aborts the whole
 * queue-listing decode.
 */
class ServerQueueSerializationTest {

    @Test
    fun deserializesWithOnlyQueueIdPresent() {
        val json = """{"queue_id": "q1"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals("q1", queue.queueId)
        assertEquals(false, queue.available)
        assertEquals(false, queue.shuffleEnabled)
        assertEquals(RepeatMode.OFF, queue.repeatMode)
    }

    @Test
    fun deserializesUnknownRepeatModeAsOff() {
        val json = """{"queue_id": "q1", "repeat_mode": "random_future_mode"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals(
            RepeatMode.OFF,
            queue.repeatMode,
            "Unknown RepeatMode must coerce to the default OFF, not throw",
        )
    }

    @Test
    fun deserializesKnownRepeatModeNormally() {
        val json = """{"queue_id": "q1", "repeat_mode": "all"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals(RepeatMode.ALL, queue.repeatMode)
    }

    @Test
    fun listDecodeSurvivesEntryMissingOptionalFields() {
        // Collection decode is per-element; a sparse entry falls back to
        // the field defaults rather than throwing out of the list decode.
        val json = """[
            {"queue_id": "q1", "available": true, "shuffle_enabled": true, "repeat_mode": "one"},
            {"queue_id": "q2"}
        ]"""

        val queues = myJson.decodeFromString<List<ServerQueue>>(json)

        assertEquals(2, queues.size)
        assertEquals("q1", queues[0].queueId)
        assertEquals(RepeatMode.ONE, queues[0].repeatMode)
        assertEquals("q2", queues[1].queueId)
        assertEquals(RepeatMode.OFF, queues[1].repeatMode)
    }
}
