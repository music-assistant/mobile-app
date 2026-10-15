package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the mapping of the server's `play_action_in_progress` queue flag. */
class QueuePlayActionMappingTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun queue(json: String) = queueFactory.create(myJson.decodeFromString<ServerQueue>(json))

    @Test
    fun `a running play action maps to true`() {
        val info = queue("""{"queue_id":"q1","extra_attributes":{"play_action_in_progress":true}}""")

        assertEquals(true, info.playActionInProgress)
    }

    @Test
    fun `a finished play action maps to false`() {
        val info = queue("""{"queue_id":"q1","extra_attributes":{"play_action_in_progress":false}}""")

        assertEquals(false, info.playActionInProgress)
    }

    @Test
    fun `other extra attributes of any type are ignored`() {
        val info = queue(
            """{"queue_id":"q1","extra_attributes":""" +
                """{"play_action_in_progress":true,"provider_data":{"a":[1,2]},"note":"x"}}""",
        )

        assertEquals(true, info.playActionInProgress)
    }

    @Test
    fun `a queue without extra attributes is not busy`() {
        assertEquals(false, queue("""{"queue_id":"q1"}""").playActionInProgress)
        assertEquals(false, queue("""{"queue_id":"q1","extra_attributes":{}}""").playActionInProgress)
    }
}
