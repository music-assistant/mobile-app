package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the queue DJ calls of [AiRadioRepository] against the provider's answer shapes. */
class AiRadioQueueDjRepositoryTest {
    private fun repository(client: FakeAiRadioClient) =
        AiRadioRepository(client, ServiceClientMediaItemRepository(client, MediaItemFactory(client)))

    @Test
    fun hostsDecodeIgnoringAuthoringFields() = runTest {
        val client = FakeAiRadioClient(
            mutableMapOf(
                APICommands.AI_RADIO_HOSTS_LIST to """
                    [{"id":"h1","name":"Ann","instructions":"be nice","tts_engine":"",
                      "language":"","options":{},"section_ids":[]}]
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(ServerAiRadioHost("h1", "Ann")), repository(client).hosts().getOrThrow())
    }

    @Test
    fun statusDecodesTheQueueToHostMap() = runTest {
        val client = FakeAiRadioClient(
            mutableMapOf(
                APICommands.AI_RADIO_QUEUE_DJ_STATUS to """{"q1":{"host_id":"h1","station_id":"late_night"}}""",
            ),
        )

        assertEquals(mapOf("q1" to "h1"), repository(client).queueDjStatus().getOrThrow())
    }

    @Test
    fun setAnswersTheStatusMapAfterTheChange() = runTest {
        val client = FakeAiRadioClient(mutableMapOf(APICommands.AI_RADIO_QUEUE_DJ_SET to "{}"))

        assertEquals(emptyMap(), repository(client).setQueueDj("q1", null).getOrThrow())
        assertEquals(APICommands.AI_RADIO_QUEUE_DJ_SET, client.sent.single().command)
    }

    @Test
    fun aMissingPayloadFails() = runTest {
        val client = FakeAiRadioClient(mutableMapOf(APICommands.AI_RADIO_QUEUE_DJ_STATUS to null))

        assertTrue(repository(client).queueDjStatus().isFailure)
    }
}
