package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the tolerance contract of [ServerQueue] deserialization and the
 * client-side enum mapping done by [QueueFactory]: only `queue_id` is
 * required, every other field carries a default, and an unknown
 * `repeat_mode` value passes through as a raw string and gets normalized
 * to [RepeatMode.OFF] by [QueueFactory] via [RepeatMode.fromServer]. One
 * stale entry in a `List<ServerQueue>` payload never aborts the decode.
 */
class ServerQueueSerializationTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    @Test
    fun deserializesWithOnlyQueueIdPresent() {
        val json = """{"queue_id": "q1"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals("q1", queue.queueId)
        assertEquals(false, queue.available)
        assertEquals(false, queue.shuffleEnabled)
        assertNull(queue.repeatMode)
        assertEquals(RepeatMode.OFF, queueFactory.create(queue).repeatMode)
    }

    @Test
    fun mapsUnknownRepeatModeToOff() {
        val json = """{"queue_id": "q1", "repeat_mode": "random_future_mode"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals("random_future_mode", queue.repeatMode)
        assertEquals(
            RepeatMode.OFF,
            queueFactory.create(queue).repeatMode,
            "Unknown RepeatMode must fall back to OFF, not throw",
        )
    }

    @Test
    fun mapsKnownRepeatModeNormally() {
        val json = """{"queue_id": "q1", "repeat_mode": "all"}"""

        val queue = myJson.decodeFromString<ServerQueue>(json)

        assertEquals(RepeatMode.ALL, queueFactory.create(queue).repeatMode)
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
        assertEquals(RepeatMode.ONE, queueFactory.create(queues[0]).repeatMode)
        assertEquals("q2", queues[1].queueId)
        assertEquals(RepeatMode.OFF, queueFactory.create(queues[1]).repeatMode)
    }
}
