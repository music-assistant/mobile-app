package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.activeItemId
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pins the mapping of the server's `ended` queue flag and the current item it hides. */
class QueueEndedMappingTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun queue(json: String) = queueFactory.create(myJson.decodeFromString<ServerQueue>(json))

    private fun onLastItem(ended: Boolean) = queue(
        """{"queue_id":"q1","current_index":1,"ended":$ended,""" +
            """"current_item":{"queue_item_id":"i2","name":"Last","duration":10}}""",
    )

    @Test
    fun anEndedQueueHasNoActiveItem() {
        val info = onLastItem(ended = true)

        assertTrue(info.ended)
        assertEquals("i2", info.currentItem?.id)
        assertNull(info.activeItemId)
    }

    @Test
    fun aQueueStoppedOnItsLastItemKeepsItActive() {
        val info = onLastItem(ended = false)

        assertFalse(info.ended)
        assertEquals("i2", info.activeItemId)
    }

    @Test
    fun aServerWithoutTheFlagIsNotEnded() {
        assertFalse(queue("""{"queue_id":"q1"}""").ended)
    }
}
