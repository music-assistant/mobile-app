// Pipeline tuning value (reorder depth) inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.ktor.http.Url
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioStreamManager
import io.music_assistant.client.player.sendspin.transport.WebRTCDataChannelTransport
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Signals that the WebRTC sendspin channel was already used (goodbye sent)
 * and a full WebRTC reconnection is needed to get a fresh channel.
 */
class WebRTCSendspinChannelExhausted : Exception("WebRTC sendspin channel exhausted")

/**
 * Factory for creating SendspinClient instances with proper configuration.
 * Separates client creation logic from lifecycle management.
 * Automatically detects WebRTC vs WebSocket connection and uses appropriate transport.
 *
 * Owns a shared [AudioStreamManager] + [ClockSynchronizer] that persist across reconnections,
 * so the audio sink keeps playing from its buffer while the protocol layer reconnects.
 * Also owns the single collector that feeds the user's playback-delay setting into
 * the pipeline's wall-clock gate (`userDelayMicros`).
 */
class SendspinClientFactory(
    private val settings: SettingsRepository,
    private val mediaPlayerController: MediaPlayerController,
    private val serviceClient: ServiceClient,
    private val networkMonitor: NetworkMonitor,
) {
    private val log = Logger.withTag("SendspinClientFactory")

    // Long-lived scope for hot-tunable settings observers. Cancelled only when the
    // shared pipeline is destroyed (user logout / permanent error).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Shared audio pipeline — persists across SendspinClient reconnections
    private var sharedClockSynchronizer: ClockSynchronizer? = null
    private var sharedPipeline: AudioStreamManager? = null
    private var delayCollectorJob: Job? = null

    // The WebRTC sendspin channel is single-use: after we send client/goodbye,
    // the server tears down its handler for that channel even though it remains
    // Open at the WebRTC layer. The DataChannelWrapper instance is the canonical
    // "channel freshness" identity — a brand-new instance is created only when
    // WebRTCConnectionManager negotiates a new peer connection.
    private var lastObservedChannel: DataChannelWrapper? = null
    private var webrtcSendspinUsed = false

    /**
     * Returns the shared pipeline (and its clock synchronizer), creating them if needed.
     * Both are passed to new SendspinClient instances so the audio sink persists across reconnects.
     */
    fun currentClockSynchronizer(): ClockSynchronizer? = sharedClockSynchronizer

    fun getOrCreatePipeline(): Pair<AudioStreamManager, ClockSynchronizer> {
        val cs = sharedClockSynchronizer ?: ClockSynchronizer().also { sharedClockSynchronizer = it }
        val pipeline = sharedPipeline ?: AudioStreamManager(cs, mediaPlayerController).also {
            sharedPipeline = it
            // Seed + subscribe: user-tuned playback delay flows straight into the
            // consumer's wall-clock gate. Hot-tunable; no reconnect needed.
            it.userDelayMicros = settings.sendspinStaticDelayMs.value * 1000L
            delayCollectorJob = scope.launch {
                settings.sendspinStaticDelayMs.collect { ms ->
                    it.userDelayMicros = ms * 1000L
                }
            }
        }
        return Pair(pipeline, cs)
    }

    /**
     * Fully destroys the shared pipeline (called on user logout or persistent error).
     * Next createIfEnabled() will allocate a fresh pipeline.
     */
    suspend fun destroyPipeline() {
        log.i { "Destroying shared audio pipeline" }
        delayCollectorJob?.cancel()
        delayCollectorJob = null
        sharedPipeline?.stopStream()
        sharedPipeline?.close()
        sharedPipeline = null
        sharedClockSynchronizer = null
        // Channel-freshness state (`lastObservedChannel`, `webrtcSendspinUsed`)
        // is intentionally NOT reset here. Tearing down the audio pipeline does
        // not un-send the `client/goodbye` we shipped on the existing sendspin
        // channel — the server-side handler is gone. If the same wrapper comes
        // back on the next attempt (e.g. user disables-then-re-enables sendspin
        // without a WebRTC reconnect), we need the identity check to still
        // report `webrtcSendspinUsed=true` so the caller forces a real WebRTC
        // reconnect. Resetting alongside the pipeline would let a zombie
        // channel be reused and silently corrupt audio. The flags are
        // implicitly reset when a new peer connection produces a new wrapper
        // instance — that's the only correct trigger.
    }

    /**
     * Creates a SendspinClient if enabled and all prerequisites are met.
     *
     * @param mainConnection The main Music Assistant connection info (for server host and proxy detection)
     * @param authToken User authentication token (required for Sendspin)
     * @return Result containing SendspinClient on success, or error message on failure
     */
    suspend fun createIfEnabled(
        mainConnection: ConnectionInfo?,
        authToken: String?,
    ): Result<SendspinClient> {
        // Validate: Sendspin enabled
        if (!settings.sendspinEnabled.value) {
            return Result.failure(
                IllegalStateException("Sendspin disabled in settings"),
            )
        }

        // Validate device name (required for protocol)
        if (settings.sendspinDeviceName.value.isBlank()) {
            return Result.failure(
                IllegalStateException("Sendspin device name cannot be empty"),
            )
        }

        // Detect connection type: WebRTC or WebSocket
        val webrtcChannel = serviceClient.webrtcSendspinChannel

        // Get or create shared pipeline — persists across reconnections
        val (pipeline, clockSync) = getOrCreatePipeline()

        return try {
            if (webrtcChannel != null) {
                createWebRTCClient(webrtcChannel, pipeline, clockSync)
            } else {
                createWebSocketClient(mainConnection, authToken, pipeline, clockSync)
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to create and start Sendspin client" }
            Result.failure(e)
        }
    }

    private suspend fun createWebRTCClient(
        webrtcChannel: DataChannelWrapper,
        pipeline: AudioStreamManager,
        clockSync: ClockSynchronizer,
    ): Result<SendspinClient> {
        // Identity-based freshness check: a different DataChannelWrapper instance means
        // WebRTCConnectionManager negotiated a new peer connection, so this channel has
        // never sent goodbye and is safe to use. Reusing the same instance after goodbye
        // hits a zombie channel (Open client-side, dead server-side) — refuse and let the
        // caller force a real WebRTC reconnect.
        if (lastObservedChannel !== webrtcChannel) {
            webrtcSendspinUsed = false
            lastObservedChannel = webrtcChannel
        }
        if (webrtcSendspinUsed) {
            log.i { "Sendspin channel exhausted — need WebRTC reconnect" }
            return Result.failure(WebRTCSendspinChannelExhausted())
        }

        log.i { "Creating Sendspin client over WebRTC data channel" }

        // WebRTC SCTP can deliver out-of-order — reorder buffer covers it.
        // 8 frames (~160 ms) is plenty for LAN-class SCTP; the previous 32 added ~640 ms-
        // of group-sync lag. Raise if audible glitches appear on noisier links.
        pipeline.reorderDepth = 8

        val config = SendspinConfig(
            clientId = settings.sendspinClientId.value,
            deviceName = settings.sendspinDeviceName.value,
            codecPreference = settings.sendspinCodecPreference.value,
            // WebRTC: auth inherited from ma-api channel, no server connection needed
            serverHost = "",
            serverPort = 0,
            mainConnectionPort = null,
            authToken = null,
        )

        val client = SendspinClient(
            config = config,
            mediaPlayerController = mediaPlayerController,
            audioPipeline = pipeline,
            clockSynchronizer = clockSync,
            networkAvailable = networkMonitor.isAvailable,
        )
        val transport = WebRTCDataChannelTransport(webrtcChannel)
        client.connectWithTransport(transport)
        // Mark used only AFTER `connectWithTransport` succeeds: a thrown attach
        // hasn't sent `client/goodbye`, so the channel is still virgin and the
        // caller can retry without forcing a (slow) WebRTC peer reconnect. The
        // exhaustion guard applies to attach-success-then-goodbye, which is the
        // actual zombie-channel condition.
        webrtcSendspinUsed = true
        log.i { "Sendspin client connected via WebRTC (auth inherited, direct hello, shared pipeline)" }
        return Result.success(client)
    }

    private suspend fun createWebSocketClient(
        mainConnection: ConnectionInfo?,
        authToken: String?,
        pipeline: AudioStreamManager,
        clockSync: ClockSynchronizer,
    ): Result<SendspinClient> {
        if (mainConnection == null) {
            return Result.failure(
                IllegalStateException("No connection info available for WebSocket Sendspin"),
            )
        }
        if (authToken == null) {
            return Result.failure(
                IllegalStateException("No auth token available - user must be logged in"),
            )
        }

        val serverHost = try {
            Url(mainConnection.webUrl).host
        } catch (e: Exception) {
            log.e(e) { "Failed to parse server URL" }
            return Result.failure(
                IllegalArgumentException("Invalid server URL", e),
            )
        }

        val config = buildConfig(
            serverHost = serverHost,
            mainConnection = mainConnection,
            authToken = authToken,
        )

        // WebSocket over TCP is ordered — minimal reorder buffer, just scheduling jitter
        pipeline.reorderDepth = 2

        log.i {
            "Creating Sendspin client over WebSocket (${if (config.requiresAuth) "proxy" else "custom"} mode, shared pipeline)"
        }
        val client = SendspinClient(
            config = config,
            mediaPlayerController = mediaPlayerController,
            audioPipeline = pipeline,
            clockSynchronizer = clockSync,
            networkAvailable = networkMonitor.isAvailable,
        )
        client.start()
        log.i { "Sendspin client started via WebSocket" }
        return Result.success(client)
    }

    /**
     * Builds SendspinConfig based on user settings and connection mode.
     * Supports both proxy mode (default) and custom connection mode.
     */
    private fun buildConfig(
        serverHost: String,
        mainConnection: ConnectionInfo,
        authToken: String,
    ): SendspinConfig {
        val useCustomConnection = settings.sendspinUseCustomConnection.value

        return if (useCustomConnection) {
            // Custom connection mode: use separate Sendspin settings
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                codecPreference = settings.sendspinCodecPreference.value,
                serverHost = settings.sendspinHost.value.takeIf { it.isNotEmpty() } ?: serverHost,
                serverPort = settings.sendspinPort.value,
                serverPath = settings.sendspinPath.value,
                useTls = settings.sendspinUseTls.value,
                useCustomConnection = true,
                authToken = authToken,
                mainConnectionPort = mainConnection.port,
            )
        } else {
            // Proxy mode: use main connection settings with /sendspin path
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                codecPreference = settings.sendspinCodecPreference.value,
                serverHost = serverHost,
                serverPort = mainConnection.port,
                serverPath = "/sendspin",
                useTls = mainConnection.isTls,
                useCustomConnection = false,
                authToken = authToken,
                mainConnectionPort = mainConnection.port,
            )
        }
    }
}
