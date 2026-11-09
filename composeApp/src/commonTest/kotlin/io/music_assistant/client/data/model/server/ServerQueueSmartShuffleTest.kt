package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins `smart_shuffle_active` decoding. A server before 2.11 omits it, and that must read as off. */
class ServerQueueSmartShuffleTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun smartShuffleActive(json: String) =
        queueFactory.create(myJson.decodeFromString<ServerQueue>(json)).smartShuffleActive

    @Test
    fun activeIsRead() {
        assertEquals(true, smartShuffleActive("""{"queue_id": "q1", "smart_shuffle_active": true}"""))
    }

    @Test
    fun inactiveIsRead() {
        assertEquals(false, smartShuffleActive("""{"queue_id": "q1", "smart_shuffle_active": false}"""))
    }

    @Test
    fun absentKeyMeansInactive() {
        assertEquals(false, smartShuffleActive("""{"queue_id": "q1", "shuffle_enabled": true}"""))
    }
}
