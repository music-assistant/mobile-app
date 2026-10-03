package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Covers the queue DJ calls of [AiRadioRepository] against the provider's answer shapes. */
class AiRadioQueueDjRepositoryTest {
    private class FakeClient(private val results: Map<String, String?>) : StubServiceClient() {
        val sent = mutableListOf<Request>()

        override val events: Flow<Event<out Any>> = emptyFlow()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            sent += request
            if (request.command !in results) fail("unexpected command ${request.command}")
            return Result.success(
                Answer(
                    buildJsonObject {
                        put("message_id", "test")
                        results[request.command]?.let { put("result", myJson.parseToJsonElement(it)) }
                    },
                ),
            )
        }
    }

    private fun repository(client: FakeClient) =
        AiRadioRepository(client, ServiceClientMediaItemRepository(client, MediaItemFactory(client)))

    @Test
    fun hostsDecodeIgnoringAuthoringFields() = runTest {
        val client = FakeClient(
            mapOf(
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
        val client = FakeClient(mapOf(APICommands.AI_RADIO_QUEUE_DJ_STATUS to """{"q1":"h1"}"""))

        assertEquals(mapOf("q1" to "h1"), repository(client).queueDjStatus().getOrThrow())
    }

    @Test
    fun setAnswersTheStatusMapAfterTheChange() = runTest {
        val client = FakeClient(mapOf(APICommands.AI_RADIO_QUEUE_DJ_SET to "{}"))

        assertEquals(emptyMap(), repository(client).setQueueDj("q1", null).getOrThrow())
        assertEquals(APICommands.AI_RADIO_QUEUE_DJ_SET, client.sent.single().command)
    }

    @Test
    fun aMissingPayloadFails() = runTest {
        val client = FakeClient(mapOf(APICommands.AI_RADIO_QUEUE_DJ_STATUS to null))

        assertTrue(repository(client).queueDjStatus().isFailure)
    }
}
