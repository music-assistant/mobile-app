package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.ServerPlayer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Guards the contract of [Answer.resultAs]: a decode failure must stay
 * local to the affected RPC rather than escape into the calling coroutine.
 * An uncaught [SerializationException] on a background dispatcher on
 * Kotlin/Native aborts the whole process, so `resultAs` must:
 *
 *  - return the decoded model on success,
 *  - return `null` when `result` is absent,
 *  - return `null` (and log) when the payload shape doesn't match `T`.
 */
class AnswerTest {
    private fun envelope(resultJson: String): Answer {
        val raw = """{"message_id": "m1", "result": $resultJson}"""
        return Answer(Json.parseToJsonElement(raw) as JsonObject)
    }

    private fun envelopeNoResult(): Answer {
        val raw = """{"message_id": "m1"}"""
        return Answer(Json.parseToJsonElement(raw) as JsonObject)
    }

    @Test
    fun decodesValidResult() {
        val answer = envelope("""{"player_id": "p1", "type": "player"}""")

        val player = answer.resultAs<ServerPlayer>()

        assertNotNull(player)
        assertEquals("p1", player.playerId)
    }

    @Test
    fun returnsNullWhenResultIsAbsent() {
        val answer = envelopeNoResult()

        assertNull(answer.resultAs<ServerPlayer>())
    }

    @Test
    fun returnsNullOnUnrecoverableShapeMismatch() {
        // Use a shape that cannot possibly decode into ServerPlayer — an
        // array where an object is required. The try/catch must contain
        // the failure instead of letting it escape the coroutine.
        val answer = envelope("""[1,2,3]""")

        val player = answer.resultAs<ServerPlayer>()

        assertNull(player, "Shape mismatch must return null, not throw")
    }

    @Test
    fun returnsNullWhenResultIsWrongPrimitive() {
        val answer = envelope(""""just a string"""")

        assertNull(answer.resultAs<ServerPlayer>())
    }

    @Test
    fun messageIdIsReadable() {
        val answer = envelopeNoResult()

        assertEquals("m1", answer.messageId)
    }
}
