package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.AI_RADIO_DOMAIN
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.data.model.server.events.ProviderEvent
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.fail

/**
 * Answers `ai_radio/` commands from [results] (raw JSON, null for a missing result) and lets a
 * test push server events and flip the command session.
 */
class FakeAiRadioClient(val results: MutableMap<String, String?>) : StubServiceClient() {
    val sent = mutableListOf<Request>()
    val eventFlow = MutableSharedFlow<Event<out Any>>()
    val ready = MutableStateFlow(true)

    /** When set for a command, its answer waits until the deferred completes. */
    val holds = mutableMapOf<String, CompletableDeferred<Unit>>()

    override val events = eventFlow
    override val isReadyForCommands = ready

    override suspend fun sendRequest(request: Request): Result<Answer> {
        sent += request
        if (request.command !in results) fail("unexpected command ${request.command}")
        // Read the answer before waiting, so a hold returns what was current when the call began.
        val result = results[request.command]
        holds[request.command]?.await()
        return Result.success(
            Answer(
                buildJsonObject {
                    put("message_id", "test")
                    result?.let { put("result", myJson.parseToJsonElement(it)) }
                },
            ),
        )
    }

    /** Waits for a subscriber first: a hint emitted before the shared upstream subscribes is lost. */
    suspend fun emitHint(name: String, objectId: String = AI_RADIO_DOMAIN) {
        eventFlow.subscriptionCount.first { it > 0 }
        eventFlow.emit(ProviderEvent(EventType.PROVIDER_EVENT, objectId, buildJsonObject { put("event", name) }))
    }
}
