package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Distinguishes loss of the request's connection from a server error. */
internal class ConnectionLostException(message: String) : Exception(message)

/**
 * Handles RPC request/response correlation for the Music Assistant API.
 *
 * Manages pending request callbacks and partial result accumulation. The
 * server sends large result sets in 500-item batches with `"partial": true`;
 * this class accumulates them and delivers the merged result to the caller.
 *
 * Only the winner of atomic removal may complete a request; racing responses,
 * transport loss, and send failures must not resume the same caller twice.
 *
 * Thread-safety: [registerCallback] and [removeCallback] are called from
 * request-issuing coroutines on `Dispatchers.IO`, while [handleResponse]
 * runs on the websocket message-collector coroutine. On Kotlin/Native those
 * can run on different threads, and [kotlin.collections.HashMap] is not
 * concurrency-safe — concurrent mutations corrupt its internal buckets and
 * surface as `ArrayIndexOutOfBoundsException` out of `HashMap#addKey`. We
 * use [AtomicReference] with copy-on-write immutable maps instead; every
 * mutation is a CAS loop, and the races that matter (is this messageId
 * still pending? what partials did we accumulate?) resolve at the CAS
 * rather than at a later "check then act" step.
 *
 * @param onAuthError Called when the server returns `error_code 20`
 *   (token expired/invalid).
 * @param onError Called with the server-supplied `details` string for any
 *   other RPC error, so the app can surface it to the user.
 */
@OptIn(ExperimentalAtomicApi::class)
class RpcEngine(
    private val onAuthError: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val logger = Logger.withTag("RpcEngine")

    private val pendingResponses =
        AtomicReference<Map<String, (Result<Answer>) -> Unit>>(emptyMap())

    // Accumulated partial results: message_id -> list of result items received so far.
    private val partialResults =
        AtomicReference<Map<String, List<JsonElement>>>(emptyMap())

    /**
     * Handle an incoming message. Returns true if the message was an RPC
     * response (has `message_id`), false if the caller should process it as
     * an event or other message.
     */
    fun handleResponse(message: JsonObject): Boolean {
        val messageId = message["message_id"]?.jsonPrimitive?.contentOrNull ?: return false
        val isPartial = message["partial"]?.jsonPrimitive?.boolean == true

        if (isPartial) {
            val resultArray = message["result"]?.jsonArray
            if (resultArray == null || resultArray.isEmpty()) return true
            // Only accumulate if a callback is still registered. If the request
            // was cancelled between batches, dropping the partial avoids a leak.
            if (!pendingResponses.load().containsKey(messageId)) return true
            partialResults.update { current ->
                val existing = current[messageId].orEmpty()
                current + (messageId to existing + resultArray)
            }
            return true
        }

        // Final response — atomically remove the callback and drain partials.
        val callback = pendingResponses.getAndUpdate { it - messageId }[messageId]
            ?: return true
        val accumulated = partialResults.getAndUpdate { it - messageId }[messageId]

        val finalMessage = if (accumulated != null) {
            val merged = accumulated.toMutableList()
            message["result"]?.jsonArray?.let(merged::addAll)
            JsonObject(message.toMutableMap().apply { put("result", JsonArray(merged)) })
        } else {
            message
        }

        val answer = Answer(finalMessage)
        if (answer.json.containsKey("error_code")) {
            logger.e { "RPC error for message $messageId: $answer" }
            val errorCode = answer.json["error_code"]?.jsonPrimitive?.int
            if (errorCode == ERROR_CODE_AUTH_REQUIRED) {
                onAuthError()
            } else {
                answer.json["details"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let(onError)
            }
        }
        callback.invoke(Result.success(answer))
        return true
    }

    /** Register a pending request callback by message_id. */
    fun registerCallback(messageId: String, callback: (Result<Answer>) -> Unit) {
        pendingResponses.update { it + (messageId to callback) }
    }

    /**
     * Withdraws the callback without invoking it. Returns true if it was still pending,
     * in which case the caller now owns completion of the request.
     */
    fun removeCallback(messageId: String): Boolean {
        val removed = pendingResponses.getAndUpdate { it - messageId }.containsKey(messageId)
        // Drop any partials accumulated for a request that will never resolve.
        partialResults.update { it - messageId }
        return removed
    }

    /** Fails every pending request with [cause]. Call on every transport loss: replies never cross connections. */
    fun failAll(cause: Throwable) {
        val pending = pendingResponses.getAndUpdate { emptyMap() }
        partialResults.store(emptyMap())
        if (pending.isEmpty()) return
        logger.i { "Failing ${pending.size} pending request(s): ${cause.message}" }
        val failure = Result.failure<Answer>(cause)
        pending.values.forEach { it.invoke(failure) }
    }

    private companion object {
        // Server emits this error_code when the session needs to re-auth (token expired, etc.).
        const val ERROR_CODE_AUTH_REQUIRED = 20
    }
}

@OptIn(ExperimentalAtomicApi::class)
private inline fun <T> AtomicReference<T>.update(transform: (T) -> T) {
    while (true) {
        val current = load()
        val updated = transform(current)
        if (compareAndSet(current, updated)) return
    }
}

/**
 * Atomically apply [transform] and return the snapshot that was replaced.
 * Mirrors `java.util.concurrent.atomic.AtomicReference.getAndUpdate`; handy
 * when you want to take a value out of a map as part of the CAS.
 */
@OptIn(ExperimentalAtomicApi::class)
private inline fun <T> AtomicReference<T>.getAndUpdate(transform: (T) -> T): T {
    while (true) {
        val current = load()
        val updated = transform(current)
        if (compareAndSet(current, updated)) return current
    }
}
