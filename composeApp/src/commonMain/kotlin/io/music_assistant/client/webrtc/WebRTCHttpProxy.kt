package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * HTTP-over-WebRTC-data-channel proxy.
 *
 * Wire protocol (matches `music-assistant/frontend` → `src/plugins/remote/webrtc-transport.ts`):
 *   Request : { "type": "http-proxy-request",  "id": "...", "method": "GET", "path": "...", "headers": {...} }
 *   Response: { "type": "http-proxy-response", "id": "...", "status": 200, "headers": {...}, "body": "<hex>" }
 *
 * Rides the shared `ma-api` data channel. A Semaphore caps in-flight requests so artwork
 * bursts don't head-of-line-block control-plane RPCs on the same SCTP stream.
 */
class WebRTCHttpProxy(
    private val sender: suspend (JsonObject) -> Unit,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
) {
    private val logger = Logger.withTag("WebRTCHttpProxy")

    private val pendingMutex = Mutex()

    // CRITICAL: deferred carries the RAW JSON STRING, not a parsed JsonObject. Both JSON parsing
    // AND hex decoding happen in the awaiter's coroutine — never on the message-listener coroutine.
    // For a 2 MB hex-encoded image body, full kotlinx.serialization parse is 100–500 ms; if that
    // ran on the listener it would queue every subsequent control-plane RPC and server event behind
    // each image. The listener only does a cheap regex peek to extract the request id.
    private val pending = mutableMapOf<String, CompletableDeferred<String>>()
    private val concurrencyGate = Semaphore(maxConcurrent)

    data class ProxyResponse(
        val status: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ProxyResponse

            if (status != other.status) return false
            if (headers != other.headers) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status
            result = 31 * result + headers.hashCode()
            result = 31 * result + body.contentHashCode()
            return result
        }
    }

    suspend fun get(
        path: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ProxyResponse = concurrencyGate.withPermit {
        val id = nextRequestId()
        val deferred = CompletableDeferred<String>()
        pendingMutex.withLock { pending[id] = deferred }
        val startMs = currentTimeMillis()
        logger.d { "GET $path id=$id (in-flight after acquire)" }
        val rawJsonString = try {
            sender(buildRequest(id, path, headers))
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pendingMutex.withLock { pending.remove(id) }
        }
        val response = parseResponse(rawJsonString)
        logger.d { "← id=$id status=${response.status} bytes=${response.body.size} in ${currentTimeMillis() - startMs}ms" }
        response
    }

    /**
     * Called by the transport tap with the RAW JSON string of an `http-proxy-response` frame.
     * Cheap by design — just resolves the deferred. No full JSON parse, no hex decode.
     */
    suspend fun dispatchRawResponse(rawJsonString: String) {
        val id = extractId(rawJsonString)
        if (id == null) {
            logger.w { "Dropping http-proxy-response without id" }
            return
        }
        val deferred = pendingMutex.withLock { pending[id] }
        if (deferred == null) {
            logger.w { "No pending request for id=$id (timed out or cancelled)" }
            return
        }
        deferred.complete(rawJsonString)
    }

    /** Fail every in-flight request. Call on transport disconnect. */
    suspend fun cancelAll(cause: Throwable) {
        val snapshot = pendingMutex.withLock {
            val all = pending.values.toList()
            pending.clear()
            all
        }
        snapshot.forEach { it.completeExceptionally(cause) }
    }

    private fun buildRequest(
        id: String,
        path: String,
        headers: Map<String, String>,
    ): JsonObject = buildJsonObject {
        put("type", "http-proxy-request")
        put("id", id)
        put("method", "GET")
        put("path", path)
        put(
            "headers",
            buildJsonObject { headers.forEach { (k, v) -> put(k, JsonPrimitive(v)) } },
        )
    }

    // Runs in the awaiter's coroutine. Both the JSON parse and the hex→bytes conversion happen
    // here on Dispatchers.Default — never on the message-listener coroutine — so a large image
    // response can't stall control-plane traffic on the shared `ma-api` channel.
    private suspend fun parseResponse(rawJsonString: String): ProxyResponse = withContext(Dispatchers.Default) {
        val json = myJson.decodeFromString<JsonObject>(rawJsonString)
        val status = json["status"]?.jsonPrimitive?.intOrNull ?: 0
        val headers = json["headers"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
            .orEmpty()
        val bodyHex = json["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        ProxyResponse(status, headers, hexToBytes(bodyHex))
    }

    private fun extractId(rawJsonString: String): String? {
        // Cheap regex scan — bounded to first 256 chars (id is small, comes early in the object).
        val head = rawJsonString.substring(0, minOf(ID_SCAN_WINDOW, rawJsonString.length))
        return ID_REGEX.find(head)?.groupValues?.get(1)
    }

    private fun nextRequestId(): String {
        val n = requestCounter++
        return "req_${currentTimeMillis()}_$n"
    }

    private var requestCounter = 0L

    companion object {
        // 2 in-flight requests is a compromise: enough to overlap network/decode work, low
        // enough to leave room on the shared `ma-api` SCTP stream for control-plane events.
        private const val DEFAULT_MAX_CONCURRENT = 2
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val ID_SCAN_WINDOW = 256

        // Matches both `"id":"..."` and `"id": "..."` (with optional whitespace).
        private val ID_REGEX = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        private const val RADIX = 16
        private const val SHIFT = 4

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            val out = ByteArray(hex.length / 2)
            var i = 0
            while (i < hex.length) {
                out[i / 2] = ((hex[i].digitToInt(RADIX) shl SHIFT) or hex[i + 1].digitToInt(RADIX)).toByte()
                i += 2
            }
            return out
        }
    }
}
