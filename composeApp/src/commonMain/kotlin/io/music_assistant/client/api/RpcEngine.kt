package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles RPC request/response correlation for the Music Assistant API.
 *
 * Manages pending request callbacks and partial result accumulation.
 * The MA server sends large result sets in 500-item batches with "partial": true;
 * this class accumulates them and delivers the merged result to the caller.
 *
 * @param onAuthError Called when the server returns error_code 20 (token expired/invalid).
 */
class RpcEngine(private val onAuthError: () -> Unit) {

    private val logger = Logger.withTag("RpcEngine")
    private val pendingResponses = mutableMapOf<String, (Answer) -> Unit>()

    // Accumulated partial results: message_id -> list of result items received so far.
    private val partialResults = mutableMapOf<String, MutableList<JsonElement>>()

    /**
     * Handle an incoming message. Returns true if the message was an RPC response
     * (has "message_id"), false if the caller should process it as an event or other message.
     */
    fun handleResponse(message: JsonObject): Boolean {
        val messageId = message["message_id"]?.jsonPrimitive?.content ?: return false
        val isPartial = message["partial"]?.jsonPrimitive?.boolean == true

        if (isPartial) {
            // Accumulate partial batch — don't resolve the callback yet.
            val resultArray = message["result"]?.jsonArray
            if (resultArray != null && resultArray.isNotEmpty() && pendingResponses.containsKey(messageId)) {
                val list = partialResults.getOrPut(messageId) { mutableListOf() }
                list.addAll(resultArray)
            }
            return true
        }

        // Final response — remove callback and any accumulated partials.
        val callback = pendingResponses.remove(messageId) ?: return true
        val accumulated = partialResults.remove(messageId)

        val finalMessage = if (accumulated != null) {
            val finalArray = message["result"]?.jsonArray
            if (finalArray != null) accumulated.addAll(finalArray)
            JsonObject(message.toMutableMap().apply { put("result", JsonArray(accumulated)) })
        } else {
            message
        }

        val answer = Answer(finalMessage)
        if (answer.json.containsKey("error_code")) {
            logger.e { "RPC error for message $messageId: $answer" }
            if (answer.json["error_code"]?.jsonPrimitive?.int == 20) {
                onAuthError()
            }
        }
        callback.invoke(answer)
        return true
    }

    /** Register a pending request callback by message_id. */
    fun registerCallback(messageId: String, callback: (Answer) -> Unit) {
        pendingResponses[messageId] = callback
    }

    /** Remove a pending request callback (for cancellation on send failure). */
    fun removeCallback(messageId: String) {
        pendingResponses.remove(messageId)
    }

    /** Cancel all pending requests — call on disconnect to prevent leaks. */
    fun clear() {
        pendingResponses.clear()
        partialResults.clear()
    }
}
