// Log-payload truncation lengths and connection delays are inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.SignalingClient
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import io.music_assistant.client.webrtc.model.WebRTCConnectionState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.io.encoding.Base64

private const val HTTP_PROXY_TYPE_SCAN_WINDOW = 256
private const val HTTP_PROXY_TYPE_TOKEN = "\"type\":\"http-proxy-response\""
private const val CHUNK_TYPE_TOKEN = "\"__chunk__\""
private const val MAX_CHUNK_COUNT = 256

// Server API schema that introduced label-based data-channel routing and the dedicated
// `http_proxy` channel (music-assistant/server#5635, MA 2.10).
private const val HTTP_PROXY_CHANNEL_SCHEMA = 49
internal const val MAX_PENDING_CHUNK_GROUPS = 16
private const val MAX_REASSEMBLED_BYTES = 16 * 1024 * 1024

/** Reassembles server-side `__chunk__` envelopes while passing legacy whole messages through. */
internal class WebRTCChunkReassembler {
    private class PendingGroup(
        val count: Int,
        val parts: Array<ByteArray?>,
        var received: Int = 0,
        var totalBytes: Int = 0,
    )

    // Insertion order lets us evict the oldest incomplete group if a buggy peer continuously
    // starts groups without finishing them. The instance is confined to one listener coroutine.
    private val groups = linkedMapOf<Int, PendingGroup>()

    fun accept(message: String): String? {
        // The server always emits `type` first. Bound the fast scan so legacy multi-MB responses
        // do not incur another full payload pass before the existing bounded proxy-type scan.
        val scanEnd = minOf(HTTP_PROXY_TYPE_SCAN_WINDOW, message.length)
        if (message.indexOf(CHUNK_TYPE_TOKEN) !in 0 until scanEnd) return message

        val frame = runCatching { myJson.decodeFromString<JsonObject>(message) }.getOrNull()
            ?: return message
        if (frame.string("type") != "__chunk__") return message

        // Once identified as a chunk envelope, malformed frames are consumed rather than leaked
        // into the normal API dispatcher. A later valid frame/group can still be processed.
        val id = frame.int("id") ?: return null
        val seq = frame.int("seq") ?: return null
        val count = frame.int("count") ?: return null
        val encoded = frame.string("b64") ?: return null
        if (count !in 1..MAX_CHUNK_COUNT || seq !in 0 until count) return null

        val existing = groups[id]
        if (existing != null && existing.count != count) return null
        val bytes = runCatching { Base64.decode(encoded) }.getOrNull() ?: return null
        val pending = existing ?: registerGroup(id, count)
        val previous = pending.parts[seq]
        val newTotal = pending.totalBytes - (previous?.size ?: 0) + bytes.size
        if (newTotal > MAX_REASSEMBLED_BYTES) {
            groups.remove(id)
            return null
        }
        if (previous == null) pending.received++
        pending.parts[seq] = bytes
        pending.totalBytes = newTotal
        if (pending.received < pending.count) return null

        val assembled = ByteArray(pending.totalBytes)
        var offset = 0
        // received == count guarantees every valid sequence slot has been populated.
        for (part in pending.parts) {
            val bytes = checkNotNull(part)
            bytes.copyInto(assembled, destinationOffset = offset)
            offset += bytes.size
        }
        groups.remove(id)
        return runCatching { assembled.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    }

    private fun registerGroup(id: Int, count: Int): PendingGroup {
        if (groups.size >= MAX_PENDING_CHUNK_GROUPS) {
            groups.remove(groups.keys.first())
        }
        return PendingGroup(count, arrayOfNulls(count)).also { groups[id] = it }
    }
}

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun isHttpProxyResponse(jsonString: String): Boolean {
    val end = minOf(HTTP_PROXY_TYPE_SCAN_WINDOW, jsonString.length)
    // Compact form (no whitespace) is what `json.dumps` / `JSON.stringify` produce by default.
    if (jsonString.regionMatches(0, "{", 0, 1) &&
        jsonString.indexOf(HTTP_PROXY_TYPE_TOKEN, startIndex = 0, ignoreCase = false) in 0 until end
    ) {
        return true
    }
    // Whitespace-tolerant fallback (rare — only if server pretty-prints).
    return HTTP_PROXY_TYPE_REGEX.containsMatchIn(jsonString.substring(0, end))
}

private val HTTP_PROXY_TYPE_REGEX = Regex("\"type\"\\s*:\\s*\"http-proxy-response\"")

class WebRTCTransport(
    private val httpClient: HttpClient,
    private val remoteId: RemoteId,
    parentScope: CoroutineScope,
    private val networkAvailable: StateFlow<Boolean>? = null,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
) : Transport {
    private val logger = Logger.withTag("WebRTCTransport")

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable) { "Uncaught exception in WebRTCTransport scope" }
        when (_state.value) {
            TransportState.Connected -> forceReconnect()
            TransportState.Connecting, is TransportState.Reconnecting ->
                _state.value = TransportState.Failed(
                    Exception("Recovery machinery died: ${throwable.message}", throwable),
                )
            TransportState.Disconnected, is TransportState.Failed -> Unit
        }
    }

    private val scope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext +
            SupervisorJob(parentScope.coroutineContext[Job]) +
            exceptionHandler,
    )

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state = _state.asStateFlow()

    // Larger buffer: under image-burst load on the shared `ma-api` channel, RpcEngine can lag
    // briefly behind the listener. A bigger buffer avoids backpressuring the listener (which
    // would otherwise stall hex-decoded image responses behind control-plane frame parsing).
    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    override val messages = _messages.asSharedFlow()

    private var manager: WebRTCConnectionManager? = null
    private var connectionJob: Job? = null
    private var messageListenerJob: Job? = null
    private var stateMonitorJob: Job? = null
    private var reconnectionJob: Job? = null
    private var networkWatchJob: Job? = null
    private var httpProxyChannelJob: Job? = null

    // Feature detection runs once per connection; cleared with the manager it belongs to.
    private var httpProxyChannelRequested = false

    val sendspinDataChannel: DataChannelWrapper?
        get() = manager?.sendspinDataChannel

    val httpProxy: WebRTCHttpProxy = WebRTCHttpProxy(sender = { json -> send(json) })

    override fun connect() {
        connectionJob?.cancel()
        startNetworkWatchIfNeeded()
        connectionJob = scope.launch {
            _state.value = TransportState.Connecting
            connectInternal(isReconnect = false)
        }
    }

    /**
     * Observes the OS-level default network. When it transitions from available → lost
     * while we have a live connection, proactively tear down and kick reconnection
     * instead of waiting for libwebrtc's ICE keepalive to notice (~6s on Android).
     *
     * Does NOT catch mid-call link-quality degradation (weak signal, packet loss) — the
     * OS still considers the interface up in that case; only ICE/keepalive timeouts fire.
     */
    private fun startNetworkWatchIfNeeded() {
        val net = networkAvailable ?: return
        if (networkWatchJob?.isActive == true) return
        networkWatchJob = scope.launch {
            var wasAvailable = net.value
            net.collect { available ->
                if (wasAvailable && !available && _state.value is TransportState.Connected) {
                    logger.w { "Default network lost — proactively aborting connection (skipping libwebrtc ICE timeout)" }
                    onNetworkLost()
                }
                wasAvailable = available
            }
        }
    }

    private fun onNetworkLost() {
        // Pre-empt the slow path: cancel state monitor, tear down manager, and start the
        // reconnection loop. The loop gates on networkAvailable, so it waits until a
        // network is back before attempting. connectionJob is also cancelled so an
        // in-flight forceReconnect doesn't race a second startReconnection() against ours.
        stateMonitorJob?.cancel()
        messageListenerJob?.cancel()
        connectionJob?.cancel()
        reconnectionJob?.cancel()
        reconnectionJob = scope.launch {
            cleanupManager()
            startReconnection()
        }
    }

    private suspend fun connectInternal(isReconnect: Boolean) {
        try {
            // Clean up old manager (does not cancel reconnectionJob — we may be inside it)
            cleanupManager()
            val mgr = createManager()
            manager = mgr
            mgr.connect(remoteId)

            // Wait for terminal WebRTC connection state (Connected or Error)
            val result = mgr.connectionState.first { connState ->
                connState is WebRTCConnectionState.Connected || connState is WebRTCConnectionState.Error
            }

            when (result) {
                is WebRTCConnectionState.Connected -> {
                    _state.value = TransportState.Connected
                    startMessageListener(mgr)
                    startStateMonitor(mgr)
                }

                is WebRTCConnectionState.Error -> {
                    if (!isReconnect) {
                        _state.value = TransportState.Failed(
                            Exception("WebRTC connection failed: ${result.error}"),
                        )
                    }
                }

                else -> {} // unreachable
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!isReconnect) {
                _state.value = TransportState.Failed(e)
            }
        }
    }

    private fun startMessageListener(mgr: WebRTCConnectionManager) {
        messageListenerJob?.cancel()
        messageListenerJob = scope.launch {
            // Listener-local ownership avoids mutable reassembly state racing lifecycle teardown.
            val chunkReassembler = WebRTCChunkReassembler()
            try {
                mgr.incomingMessages.collect { wireMessage ->
                    try {
                        // New servers chunk messages that exceed libdatachannel's 256 KiB limit.
                        // Legacy servers still send whole messages, which pass through unchanged.
                        val jsonString = chunkReassembler.accept(wireMessage) ?: return@collect

                        // CHEAP peek: avoid full JSON parse for multi-MB http-proxy-response frames.
                        // The full parse would block the listener (single coroutine), queueing every
                        // subsequent control-plane message behind each image body for hundreds of ms.
                        // Bounded to first 256 chars — `type` is always early in the object.
                        if (isHttpProxyResponse(jsonString)) {
                            httpProxy.dispatchRawResponse(jsonString)
                        } else {
                            val jsonObject = myJson.decodeFromString<JsonObject>(jsonString)
                            maybeOpenHttpProxyChannel(mgr, jsonObject)
                            _messages.emit(jsonObject)
                        }
                    } catch (e: Exception) {
                        // For chunked traffic wireMessage is only the final envelope, not the
                        // reassembled payload, so identify the stage without mislabeling the frame.
                        logger.e(e) { "Failed to dispatch incoming WebRTC frame: ${wireMessage.take(200)}" }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.d { "WebRTC message listener ended: ${e.message}" }
            }
        }
    }

    /**
     * Opens the dedicated image channel once per connection, as soon as the server proves it
     * can route the label. `server_info` is the first frame on `ma-api` and carries the
     * schema version; a server below [HTTP_PROXY_CHANNEL_SCHEMA] mistakes an unknown label
     * for the API channel and drops the whole session, so this must never be speculative.
     */
    private fun maybeOpenHttpProxyChannel(mgr: WebRTCConnectionManager, message: JsonObject) {
        if (httpProxyChannelRequested) return
        val schema = (message["schema_version"] as? JsonPrimitive)?.intOrNull ?: return
        if (schema < HTTP_PROXY_CHANNEL_SCHEMA) {
            logger.i { "Server schema $schema predates the image channel — proxying over ma-api" }
            httpProxyChannelRequested = true
            return
        }
        httpProxyChannelRequested = true
        httpProxyChannelJob = scope.launch { serveHttpProxyChannel(mgr) }
    }

    private suspend fun serveHttpProxyChannel(mgr: WebRTCConnectionManager) {
        val channel = try {
            mgr.openHttpProxyChannel()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Proxying over the API channel remains a working fallback.
            logger.w(e) { "http_proxy channel unavailable — images stay on ma-api" }
            null
        } ?: return

        httpProxy.attachChannel { json ->
            channel.send(myJson.encodeToString(JsonObject.serializer(), json))
        }
        try {
            // Listener-local, like the ma-api reassembler: a schema-49 server that predates
            // the binary framing still answers here in hex, chunked when oversized.
            val chunkReassembler = WebRTCChunkReassembler()
            channel.inbound.collect { inbound ->
                when (inbound) {
                    is DataChannelInbound.Text ->
                        chunkReassembler.accept(inbound.text)
                            ?.let { httpProxy.dispatchProxyChannelText(it) }

                    is DataChannelInbound.Binary ->
                        httpProxy.dispatchProxyChannelBinary(inbound.bytes)
                }
            }
        } finally {
            // The inbound stream ends when the channel dies. Run the detach uncancellable so
            // callers are failed even when this job is torn down mid-collect.
            withContext(NonCancellable) {
                httpProxy.detachChannel(IllegalStateException("http_proxy channel closed"))
            }
        }
    }

    private fun startStateMonitor(mgr: WebRTCConnectionManager) {
        stateMonitorJob?.cancel()
        stateMonitorJob = scope.launch {
            // Wait for error state — first() completes when predicate matches
            val errorState = mgr.connectionState.first { it is WebRTCConnectionState.Error }
            logger.w { "WebRTC error detected: $errorState. Starting reconnection..." }
            messageListenerJob?.cancel()
            // Launch reconnection in a separate job so:
            // 1. cleanupManager() can cancel stateMonitorJob without killing reconnection
            // 2. disconnect()/forceReconnect() can cancel reconnectionJob explicitly
            reconnectionJob?.cancel()
            reconnectionJob = scope.launch { startReconnection() }
        }
    }

    private suspend fun startReconnection() {
        val reconnected = runReconnectionLoop(
            maxAttempts = maxReconnectAttempts,
            networkAvailable = networkAvailable,
            onAttemptStarting = { _state.value = TransportState.Reconnecting(it) },
            tryConnect = { attempt ->
                logger.i { "WebRTC reconnect attempt $attempt/$maxReconnectAttempts" }
                connectInternal(isReconnect = true)
                _state.value == TransportState.Connected
            },
        )
        if (!reconnected) {
            _state.value = TransportState.Failed(Exception("Max WebRTC reconnect attempts reached"))
        }
    }

    fun forceReconnect() {
        connectionJob?.cancel()
        reconnectionJob?.cancel()
        connectionJob = scope.launch {
            messageListenerJob?.cancel()
            stateMonitorJob?.cancel()
            cleanupManager()
            delay(1500) // wait for signaling server to process disconnect
            logger.i { "Starting fresh WebRTC connection after forced disconnect" }
            startReconnection()
        }
    }

    override suspend fun send(message: JsonObject) {
        val mgr = manager ?: error("Not connected")
        val jsonString = myJson.encodeToString(JsonObject.serializer(), message)
        mgr.send(jsonString)
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        reconnectionJob?.cancel()
        reconnectionJob = null
        messageListenerJob?.cancel()
        messageListenerJob = null
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        networkWatchJob?.cancel()
        networkWatchJob = null
        httpProxyChannelJob?.cancel()
        httpProxyChannelJob = null
        httpProxyChannelRequested = false
        val mgr = manager
        manager = null
        if (mgr != null) {
            scope.launch {
                mgr.disconnect()
                httpProxy.cancelAll(IllegalStateException("WebRTC transport disconnected"))
            }
        }
        _state.value = TransportState.Disconnected
    }

    override fun close() {
        scope.cancel()
    }

    /** Cleans up the current manager and its listener jobs. Does NOT cancel reconnectionJob. */
    private suspend fun cleanupManager() {
        messageListenerJob?.cancel()
        messageListenerJob = null
        stateMonitorJob?.cancel()
        stateMonitorJob = null
        httpProxyChannelJob?.cancel()
        httpProxyChannelJob = null
        httpProxyChannelRequested = false
        manager?.disconnect()
        manager = null
        httpProxy.cancelAll(IllegalStateException("WebRTC transport cleanup"))
    }

    private fun createManager(): WebRTCConnectionManager {
        val signalingClient = SignalingClient(httpClient, scope)
        val mgr = WebRTCConnectionManager(signalingClient, scope)
        logger.d { "Created new WebRTC manager [${mgr.hashCode()}]" }
        return mgr
    }
}
