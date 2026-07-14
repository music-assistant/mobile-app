package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.AudioProcessingChain
import io.music_assistant.client.data.model.server.events.AudioProcessingUpdatedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AudioProcessingApiTest {
    @Test
    fun buildsAudioProcessingChainRequest() {
        val request = Request.Queue.audioProcessingChain("queue-1")

        assertEquals(APICommands.PLAYER_QUEUES_AUDIO_PROCESSING_CHAIN, request.command)
        assertEquals("queue-1", request.args?.get("queue_id")?.jsonPrimitive?.content)
    }

    @Test
    fun decodesTypedCommandResult() {
        val answer = Answer(
            parseObject(
                """{
                    "message_id": "m1",
                    "result": {
                        "queue_id": "queue-1",
                        "queue_item_id": "item-1",
                        "revision": 5,
                        "state": "ready"
                    }
                }""",
            ),
        )

        val chain = assertNotNull(answer.resultAs<AudioProcessingChain>())
        assertEquals("queue-1", chain.queueId)
        assertEquals(5L, chain.revision)
    }

    @Test
    fun decodesAudioProcessingEventWithFutureNestedType() {
        val event = Event(
            parseObject(
                """{
                    "event": "audio_processing_updated",
                    "object_id": "queue-1",
                    "data": {
                        "queue_id": "queue-1",
                        "queue_item_id": "item-1",
                        "revision": 6,
                        "state": "future_state",
                        "outputs": [{
                            "dsp": {
                                "filters": [{"type": "future_filter"}]
                            }
                        }]
                    }
                }""",
            ),
        ).event()

        val update = assertNotNull(event as? AudioProcessingUpdatedEvent)
        assertEquals("queue-1", update.objectId)
        assertEquals("future_state", update.data?.state)
        assertEquals(1, update.data?.outputs?.single()?.dsp?.filters?.size)
    }

    @Test
    fun decodesNullAudioProcessingEventAsClear() {
        val event = Event(
            parseObject(
                """{
                    "event": "audio_processing_updated",
                    "object_id": "queue-1",
                    "data": null
                }""",
            ),
        ).event()

        val update = assertNotNull(event as? AudioProcessingUpdatedEvent)
        assertEquals("queue-1", update.objectId)
        assertNull(update.data)
    }

    private fun parseObject(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject
}
