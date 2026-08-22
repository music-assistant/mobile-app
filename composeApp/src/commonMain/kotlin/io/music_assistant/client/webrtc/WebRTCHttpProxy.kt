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
 *
 * Responses come in two framings, chosen by the channel the request went out on:
 *  - `ma-api` (any server): one JSON message with the body hex-encoded, which costs about
 *    2.7x the image once oversized-message chunking is applied on top.
 *    `{ "type": "http-proxy-response", "id", "status", "headers", "body": "<hex>" }`
 *  - `http_proxy` (server schema 49+, MA 2.10): a small JSON header carrying the body length,
 *    followed by the body as raw binary messages — the image costs its own size and no more.
 *    `{ "type": "http-proxy-response", "id", "status", "headers", "size": N }` then N bytes.
 *    Those binary frames carry no request id, so the server holds the channel for a whole
 *    reply; replies never interleave there and one reassembly slot is enough.
 *
 * A Semaphore caps in-flight requests. On `ma-api` that stops artwork bursts from
 * head-of-line-blocking control-plane RPCs on the same SCTP stream; the dedicated channel
 * exists to remove that contention entirely, but the server bounds its own concurrency at
 * the same figure so the cap stays useful there too.
 */
class WebRTCHttpProxy(
    private val sender: suspend (JsonObject) -> Unit,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
) {
    private val logger = Logger.withTag("WebRTCHttpProxy")

    private val pendingMutex = Mutex()

    /**
     * What a completed response hands back to the awaiter.
     *
     * [Hex] carries the RAW JSON STRING, not a parsed JsonObject: both JSON parsing AND hex
     * decoding must happen in the awaiter's coroutine, never on the message-listener
     * coroutine. For a 2 MB hex-encoded body a full kotlinx.serialization parse is
     * 100–500 ms; on the listener that would queue every subsequent control-plane RPC and
     * server event behind each image. The listener only peeks out the request id.
     *
     * [Binary] is already decoded — its body arrived as raw bytes, so there is nothing left
     * to parse and no reason to defer it.
     */
    private sealed interface Payload {
        class Hex(val raw: String) : Payload
        class Binary(val response: ProxyResponse) : Payload
    }

    /** Identifies one lifetime of the dedicated proxy channel. */
    class ChannelAttachment internal constructor()

    /**
     * @param attachment the dedicated channel generation used for this request, or null when
     *   the request went out on `ma-api`.
     */
    private class Pending(
        val deferred: CompletableDeferred<Payload>,
        val attachment: ChannelAttachment?,
    )

    /** A binary reply being reassembled on the dedicated channel: its header, then its body. */
    private class PendingBody(
        val id: String,
        val status: Int,
        val headers: Map<String, String>,
        val size: Int,
    ) {
        val parts = mutableListOf<ByteArray>()
        var received: Int = 0
    }

    private val pending = mutableMapOf<String, Pending>()

    // Sends on the dedicated image channel; null whenever there isn't one. Guarded by
    // `pendingMutex` together with `pending`, so a request cannot register itself as
    // proxy-channel-bound after detachChannel has already failed that generation.
    private var proxySender: (suspend (JsonObject) -> Unit)? = null
    private var proxyAttachment: ChannelAttachment? = null

    // Single slot: the server holds a send lock for header-plus-body on this channel.
    private var pendingBody: PendingBody? = null

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
            val deferred = CompletableDeferred<Payload>()
            // Snapshot the channel and register under one lock, so a detach racing this
            // cannot leave an entry nobody will ever fail waiting out its full timeout.
            val send = pendingMutex.withLock {
                val proxy = proxySender
                pending[id] = Pending(deferred, attachment = proxyAttachment)
                proxy ?: sender
            }
            logger.d { "GET $path id=$id acquire_wait_ms=$acquireWaitMs" }
            val payload = try {
                send(buildRequest(id, path, headers))
                withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pendingMutex.withLock {
                    pending.remove(id)
                    // Stop buffering frames nothing is waiting for any more.
                    if (pendingBody?.id == id) pendingBody = null
                }
            }
            val parseStartMs = currentTimeMillis()
            val response = when (payload) {
                is Payload.Binary -> payload.response
                is Payload.Hex -> parseResponse(payload.raw)
            }
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
     * Routes subsequent requests onto the dedicated image channel. Requests already in
     * flight on `ma-api` stay there — the server answers on the channel a request arrived on.
     */
    suspend fun attachChannel(send: suspend (JsonObject) -> Unit): ChannelAttachment {
        val attachment = ChannelAttachment()
        pendingMutex.withLock {
            proxySender = send
            proxyAttachment = attachment
        }
        return attachment
    }

    /**
     * The dedicated channel is gone. Fails everything that went out on it at once, rather
     * than leaving each caller to wait out its timeout, and drops a half-received body that
     * can never be completed. Requests in flight on `ma-api` are deliberately left alone.
     *
     * A stale listener may run after a newer channel has attached. Only clear the shared
     * sender and reassembly state when [attachment] still owns the current channel.
     * Requests that were sent through [attachment] are failed in either case.
     */
    suspend fun detachChannel(attachment: ChannelAttachment, cause: Throwable) {
        val doomed = pendingMutex.withLock {
            if (proxyAttachment === attachment) {
                proxySender = null
                proxyAttachment = null
                pendingBody = null
            }
            val dead = pending.filterValues { it.attachment === attachment }
            dead.keys.forEach { pending.remove(it) }
            dead.values.toList()
        }
        if (doomed.isNotEmpty()) {
            logger.w { "http_proxy channel closed with ${doomed.size} request(s) in flight" }
        }
        doomed.forEach { it.deferred.completeExceptionally(cause) }
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
        val entry = pendingMutex.withLock { pending[id] }
        if (entry == null) {
            logger.w { "No pending request for id=$id (timed out or cancelled)" }
            return
        }
        entry.deferred.complete(Payload.Hex(rawJsonString))
    }

    /**
     * Called with a text frame from the dedicated image channel, which is either a binary
     * response header or — from a schema-49 server that predates the binary framing — a
     * whole hex-in-JSON response.
     */
    suspend fun dispatchProxyChannelText(rawJsonString: String) {
        // A header is a few hundred bytes; a hex response is megabytes. Full-parsing only
        // the small frame keeps `size` from ever being confused with a same-named response
        // header, and never walks a multi-MB hex body an extra time.
        if (rawJsonString.length > PROXY_HEADER_MAX_CHARS) {
            dispatchRawResponse(rawJsonString)
            return
        }
        val frame = runCatching { myJson.decodeFromString<JsonObject>(rawJsonString) }.getOrNull()
        val size = (frame?.get("size") as? JsonPrimitive)?.intOrNull
        if (frame == null || size == null) {
            // No body length: the hex-in-JSON form, which the existing path already handles.
            dispatchRawResponse(rawJsonString)
            return
        }
        beginBinaryBody(frame, size)
    }

    /** Called with a raw body frame from the dedicated image channel. */
    suspend fun dispatchProxyChannelBinary(bytes: ByteArray) {
        val complete = pendingMutex.withLock {
            // Frames carry no id, so one arriving with no header open is unattributable.
            val body = pendingBody ?: return
            body.parts.add(bytes)
            body.received += bytes.size
            body.received >= body.size
        }
        if (complete) completeBinaryBody()
    }

    private suspend fun beginBinaryBody(frame: JsonObject, size: Int) {
        val id = (frame["id"] as? JsonPrimitive)?.contentOrNull ?: run {
            logger.w { "Dropping http-proxy-response header without id" }
            return
        }
        if (size !in 0..MAX_BODY_BYTES) {
            logger.w { "Rejecting http-proxy-response id=$id with implausible size=$size" }
            return
        }
        val status = (frame["status"] as? JsonPrimitive)?.intOrNull ?: 0
        val headers = frame["headers"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
            .orEmpty()
        val waiting = pendingMutex.withLock {
            // No point buffering a body for a request that already gave up.
            if (pending[id] == null) {
                pendingBody = null
                false
            } else {
                pendingBody = PendingBody(id, status, headers, size)
                true
            }
        }
        if (!waiting) {
            logger.w { "No pending request for id=$id (timed out or cancelled)" }
            return
        }
        // An empty body is sent as a header on its own, with no frame to follow.
        if (size == 0) completeBinaryBody()
    }

    private suspend fun completeBinaryBody() {
        // Check for a waiting caller before assembling, which for an image copies real bytes.
        val (body, entry) = pendingMutex.withLock {
            val b = pendingBody ?: return
            pendingBody = null
            b to pending.remove(b.id)
        }
        val waiting = entry ?: return
        val assembled = ByteArray(body.size)
        var offset = 0
        for (part in body.parts) {
            val n = minOf(part.size, body.size - offset)
            if (n <= 0) break
            part.copyInto(assembled, destinationOffset = offset, startIndex = 0, endIndex = n)
            offset += n
        }
        waiting.deferred.complete(
            Payload.Binary(ProxyResponse(body.status, body.headers, assembled)),
        )
    }

    /** Fail every in-flight request. Call on transport disconnect. */
    suspend fun cancelAll(cause: Throwable) {
        val snapshot = pendingMutex.withLock {
            val all = pending.values.toList()
            pending.clear()
            pendingBody = null
            proxySender = null
            proxyAttachment = null
            all
        }
        snapshot.forEach { it.deferred.completeExceptionally(cause) }
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

        // A binary response header is a few hundred bytes. Anything larger on the dedicated
        // channel is a hex-in-JSON response, which must not be full-parsed on the listener.
        private const val PROXY_HEADER_MAX_CHARS = 8 * 1024

        // Upper bound on a single proxied body, mirroring the transport's reassembly guard.
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024

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
