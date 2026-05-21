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
    ): ProxyResponse {
        // Instrumentation (Phase 2a): measure how long callers wait for the semaphore vs
        // how long they hold it. acquire_wait_ms close to 0 → semaphore isn't the bottleneck;
        // sustained acquire_wait_ms ≫ held_ms → caller queue is starving on the gate.
        val queuedAtMs = currentTimeMillis()
        return concurrencyGate.withPermit {
            val permitAcquiredMs = currentTimeMillis()
            val acquireWaitMs = permitAcquiredMs - queuedAtMs
            val id = nextRequestId()
            val deferred = CompletableDeferred<String>()
            pendingMutex.withLock { pending[id] = deferred }
            logger.d { "GET $path id=$id acquire_wait_ms=$acquireWaitMs" }
            val rawJsonString = try {
                sender(buildRequest(id, path, headers))
                withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pendingMutex.withLock { pending.remove(id) }
            }
            val parseStartMs = currentTimeMillis()
            val response = parseResponse(rawJsonString)
            val nowMs = currentTimeMillis()
            logger.d {
                "← id=$id status=${response.status} bytes=${response.body.size} " +
                    "acquire_wait_ms=$acquireWaitMs held_ms=${nowMs - permitAcquiredMs} " +
                    "parse_ms=${nowMs - parseStartMs}"
            }
            response
        }
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

    // Runs in the awaiter's coroutine on Dispatchers.Default — never on the message-listener.
    // Fast path scans the frame with indexOf, never materialising the full JsonObject (the hex
    // `body` field would otherwise allocate ~2× the wire-size as a JsonPrimitive). On any
    // extraction failure we fall back to the full kotlinx parse, behind a one-line warn so
    // divergence from the wire schema is visible.
    private suspend fun parseResponse(rawJsonString: String): ProxyResponse = withContext(Dispatchers.Default) {
        fastParseResponse(rawJsonString) ?: run {
            logger.w { "Falling back to full kotlinx parse for http-proxy-response" }
            slowParseResponse(rawJsonString)
        }
    }

    private fun fastParseResponse(raw: String): ProxyResponse? {
        // `body` is the only large field. Locate `"body":"` and read until the closing `"`.
        // Hex is `[0-9a-fA-F]` only — no escapes to worry about. Frame is compact JSON
        // (server uses json.dumps, no whitespace), so we don't tolerate spaces around `:`.
        val bodyKeyIdx = raw.indexOf(BODY_KEY)
        if (bodyKeyIdx < 0) return null
        val bodyStart = bodyKeyIdx + BODY_KEY.length
        val bodyEnd = raw.indexOf('"', startIndex = bodyStart)
        if (bodyEnd < 0) return null

        // status / headers always precede `body` in the wire format. Restrict the scan window
        // to the prefix so we never re-walk megabytes of hex.
        val status = findStatusBefore(raw, bodyKeyIdx) ?: return null
        val headers = extractHeadersBefore(raw, bodyKeyIdx) ?: return null

        val body = hexToBytes(raw, bodyStart, bodyEnd)
        return ProxyResponse(status, headers, body)
    }

    private fun findStatusBefore(raw: String, limit: Int): Int? {
        val keyIdx = raw.indexOf(STATUS_KEY)
        if (keyIdx !in 0..<limit) return null
        var i = keyIdx + STATUS_KEY.length
        // skip optional whitespace (defensive — server emits compact JSON, but cheap)
        while (i < limit && raw[i].isWhitespace()) i++
        var value = 0
        var any = false
        while (i < limit) {
            val c = raw[i]
            if (c !in '0'..'9') break
            value = value * RADIX_10 + (c.code - '0'.code)
            any = true
            i++
        }
        return if (any) value else null
    }

    private fun extractHeadersBefore(raw: String, limit: Int): Map<String, String>? {
        val keyIdx = raw.indexOf(HEADERS_KEY)
        if (keyIdx !in 0..<limit) return emptyMap()
        val objStart = raw.indexOf('{', startIndex = keyIdx + HEADERS_KEY.length)
        if (objStart !in 0..<limit) return null
        val objEnd = raw.indexOf('}', startIndex = objStart)
        if (objEnd !in 0..<limit) return null
        // Headers are a small flat string→string object — parse only this slice.
        return runCatching {
            myJson.decodeFromString<JsonObject>(raw.substring(objStart, objEnd + 1))
                .mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
        }.getOrNull()
    }

    private fun slowParseResponse(rawJsonString: String): ProxyResponse {
        val json = myJson.decodeFromString<JsonObject>(rawJsonString)
        val status = json["status"]?.jsonPrimitive?.intOrNull ?: 0
        val headers = json["headers"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
            .orEmpty()
        val bodyHex = json["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return ProxyResponse(status, headers, hexToBytes(bodyHex))
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
        // 6 in-flight requests, raised from the original conservative `2` after real-workload
        // logging (Phase 2a) showed acquire_wait_ms reaching ~326 ms on artwork-burst screens
        // while held_ms stayed at ~100 ms — i.e. the gate, not the network, was the
        // bottleneck. Typical artwork bodies on this codepath are 50–450 KB, well below the
        // multi-MB blobs the original `2` was defending against. If control-plane RPC latency
        // regresses noticeably under sustained image bursts, drop to 4.
        private const val DEFAULT_MAX_CONCURRENT = 6
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val ID_SCAN_WINDOW = 256

        // Matches both `"id":"..."` and `"id": "..."` (with optional whitespace).
        private val ID_REGEX = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        private const val RADIX = 16
        private const val RADIX_10 = 10
        private const val SHIFT = 4

        // Compact-JSON keys the wire uses (server emits via `json.dumps`, no whitespace).
        // If the server ever pretty-prints, fastParseResponse returns null and we fall back.
        private const val BODY_KEY = "\"body\":\""
        private const val STATUS_KEY = "\"status\":"
        private const val HEADERS_KEY = "\"headers\":"

        fun hexToBytes(hex: String): ByteArray = hexToBytes(hex, 0, hex.length)

        fun hexToBytes(src: String, start: Int, endExclusive: Int): ByteArray {
            val len = endExclusive - start
            require(len % 2 == 0) { "Hex range must have even length" }
            val out = ByteArray(len / 2)
            var i = start
            var o = 0
            while (i < endExclusive) {
                out[o++] = ((src[i].digitToInt(RADIX) shl SHIFT) or src[i + 1].digitToInt(RADIX)).toByte()
                i += 2
            }
            return out
        }
    }
}
