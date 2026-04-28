package io.music_assistant.client.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards both the functional contract and the thread-safety contract of
 * [RpcEngine].
 *
 * Functional: register a callback, feed the matching response (including
 * partial-then-final sequences), and the callback fires with the merged
 * `result`. `removeCallback` and `clear` prevent firing; auth error
 * code 20 triggers the [onAuthError] hook.
 *
 * Thread-safety: `registerCallback` / `removeCallback` run on the
 * request-issuing coroutine, while `handleResponse` runs on the websocket
 * message-collector coroutine. On Kotlin/Native those dispatch to
 * different threads, so the two `AtomicReference<Map>` fields must behave
 * correctly under real contention. Two stress tests exercise this on
 * `Dispatchers.Default` — on JVM unit tests that's the real multi-thread
 * pool, which is where the plain-`HashMap` implementation used to corrupt
 * its bucket array and throw `ArrayIndexOutOfBoundsException`.
 */
class RpcEngineTest {
    private fun engine(onAuthError: () -> Unit = {}): RpcEngine = RpcEngine(onAuthError)

    private fun message(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun finalResponseFiresRegisteredCallback() {
        val engine = engine()
        var received: Answer? = null
        engine.registerCallback("m1") { received = it }

        val handled = engine.handleResponse(
            message("""{"message_id": "m1", "result": {"ok": true}}"""),
        )

        assertTrue(handled)
        val seen = received
        assertNotNull(seen)
        assertEquals("m1", seen.messageId)
    }

    @Test
    fun handleResponseReturnsFalseForNonResponseMessage() {
        val engine = engine()

        val handled = engine.handleResponse(
            message("""{"event": "player_updated"}"""),
        )

        assertFalse(handled, "Events must not be consumed by RpcEngine")
    }

    @Test
    fun partialResponsesAccumulateAndMergeIntoFinal() {
        val engine = engine()
        var received: Answer? = null
        engine.registerCallback("m1") { received = it }

        engine.handleResponse(
            message("""{"message_id": "m1", "partial": true, "result": [1, 2]}"""),
        )
        engine.handleResponse(
            message("""{"message_id": "m1", "partial": true, "result": [3, 4]}"""),
        )
        engine.handleResponse(
            message("""{"message_id": "m1", "result": [5]}"""),
        )

        val seen = received
        assertNotNull(seen)
        assertEquals("[1,2,3,4,5]", seen.json["result"]?.toString())
    }

    @Test
    fun partialWithoutFinalDoesNotInvokeCallback() {
        val engine = engine()
        var fired = false
        engine.registerCallback("m1") { fired = true }

        engine.handleResponse(
            message("""{"message_id": "m1", "partial": true, "result": [1]}"""),
        )

        assertFalse(fired, "Callback must wait for the non-partial final frame")
    }

    @Test
    fun removeCallbackPreventsLaterResponseFromFiring() {
        val engine = engine()
        var fired = false
        engine.registerCallback("m1") { fired = true }

        engine.removeCallback("m1")
        engine.handleResponse(
            message("""{"message_id": "m1", "result": {}}"""),
        )

        assertFalse(fired, "Cancelled request must not receive a late response")
    }

    @Test
    fun removeCallbackDropsAccumulatedPartials() {
        val engine = engine()
        engine.registerCallback("m1") { /* never invoked */ }

        // Accumulate partials, then cancel.
        engine.handleResponse(
            message("""{"message_id": "m1", "partial": true, "result": [1, 2]}"""),
        )
        engine.removeCallback("m1")

        // Register a new request with the same id and a single-shot final
        // response. The partials from the previous registration must not
        // leak into this one.
        var received: Answer? = null
        engine.registerCallback("m1") { received = it }
        engine.handleResponse(
            message("""{"message_id": "m1", "result": [99]}"""),
        )

        val seen = received
        assertNotNull(seen)
        assertEquals("[99]", seen.json["result"]?.toString())
    }

    @Test
    fun clearDropsAllPendingCallbacks() {
        val engine = engine()
        var firedA = false
        var firedB = false
        engine.registerCallback("m1") { firedA = true }
        engine.registerCallback("m2") { firedB = true }

        engine.clear()
        engine.handleResponse(message("""{"message_id": "m1", "result": {}}"""))
        engine.handleResponse(message("""{"message_id": "m2", "result": {}}"""))

        assertFalse(firedA)
        assertFalse(firedB)
    }

    @Test
    fun handleResponseOnUnknownMessageIdIsANoOp() {
        val engine = engine()

        val handled = engine.handleResponse(
            message("""{"message_id": "ghost", "result": {}}"""),
        )

        assertTrue(handled, "Still 'handled' in the RPC sense — not an event")
    }

    @Test
    fun authErrorCallbackFiresOnErrorCode20() {
        var authErrors = 0
        val engine = engine(onAuthError = { authErrors++ })
        engine.registerCallback("m1") { /* ignore */ }

        engine.handleResponse(
            message("""{"message_id": "m1", "error_code": 20, "error_message": "expired"}"""),
        )

        assertEquals(1, authErrors)
    }

    @Test
    fun authErrorNotTriggeredOnOtherErrorCodes() {
        var authErrors = 0
        val engine = engine(onAuthError = { authErrors++ })
        engine.registerCallback("m1") { /* ignore */ }

        engine.handleResponse(
            message("""{"message_id": "m1", "error_code": 500, "error_message": "server"}"""),
        )

        assertEquals(0, authErrors)
    }

    /**
     * Fires many parallel register/respond cycles on `Dispatchers.Default`
     * so the AtomicReference CAS loops inside [RpcEngine] run under real
     * contention. The contract: no thrown exception, and every registered
     * callback fires exactly once (no lost updates from a botched CAS).
     * Kotlin/Native's new memory model gives the same multi-thread
     * dispatch on the iOS simulator test target.
     */
    @Test
    fun concurrentRegisterAndHandleDoesNotCrashOrLoseCallbacks() = runBlocking {
        val engine = engine()
        val callbackCount = 500
        val fired = IntArray(callbackCount)

        coroutineScope {
            repeat(callbackCount) { idx ->
                launch(Dispatchers.Default) {
                    val id = "m$idx"
                    engine.registerCallback(id) { fired[idx] = 1 }
                    // Parallel thread delivers the response.
                    respondAsync(engine, id)
                }
            }
        }

        // All responses delivered → all callbacks fired exactly once.
        val totalFired = fired.sum()
        assertEquals(
            callbackCount,
            totalFired,
            "Every registered callback must have fired exactly once. " +
                "Lost count = ${callbackCount - totalFired}",
        )
    }

    /**
     * Exercises the CAS loop in `partialResults.update` specifically.
     * Many message ids each receive a sequence of partials and a final;
     * all of that happens in parallel across ids on `Dispatchers.Default`,
     * so CAS retries on the shared map are forced. Each callback must end
     * up with the exact expected element count — a dropped update would
     * surface as a short list.
     */
    @Test
    fun concurrentPartialAccumulationProducesCorrectMergedResult() = runBlocking {
        val engine = engine()
        val messageIds = List(20) { "m$it" }
        val receivedSizes = IntArray(messageIds.size)

        // Register everything first, then flood partials + finals in parallel.
        messageIds.forEachIndexed { idx, id ->
            engine.registerCallback(id) { answer ->
                receivedSizes[idx] =
                    answer.json["result"]?.toString()?.let { it.count { c -> c == ',' } + 1 }
                        ?: 0
            }
        }

        coroutineScope {
            messageIds.mapIndexed { idx, id ->
                async(Dispatchers.Default) {
                    repeat(10) { batch ->
                        engine.handleResponse(
                            message(
                                """{"message_id": "$id", "partial": true, "result": [$batch]}""",
                            ),
                        )
                    }
                    engine.handleResponse(
                        message("""{"message_id": "$id", "result": [999]}"""),
                    )
                    idx
                }
            }.awaitAll()
        }

        // 10 partial batches of 1 item each + 1 final item = 11 items per id.
        receivedSizes.forEachIndexed { idx, size ->
            assertEquals(
                11,
                size,
                "Message ${messageIds[idx]} lost partial items under concurrency",
            )
        }
    }

    private fun CoroutineScope.respondAsync(engine: RpcEngine, id: String) {
        launch(Dispatchers.Default) {
            engine.handleResponse(
                message("""{"message_id": "$id", "result": {"ok": true}}"""),
            )
        }
    }
}
