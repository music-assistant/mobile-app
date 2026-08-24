// Pipeline tuning value (reorder depth) inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.ktor.http.Url
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioStreamManager
import io.music_assistant.client.player.sendspin.identity.SendspinKeyStore
import io.music_assistant.client.player.sendspin.identity.SendspinTrustStore
import io.music_assistant.client.player.sendspin.model.GoodbyeReason
import io.music_assistant.client.player.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.client.player.sendspin.pairing.SilentPairingCoordinator
import io.music_assistant.client.player.sendspin.session.SessionOutcome
import io.music_assistant.client.player.sendspin.transport.WebRTCDataChannelTransport
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val keyStore: SendspinKeyStore,
) {
    private val log = Logger.withTag("SendspinClientFactory")

    private val noiseCrypto = CryptographyKotlinNoiseCrypto()

    private fun resolveEncryptionMode(): SendspinEncryptionMode {
        val schemaVersion = (serviceClient.sessionState.value as? HasConnectionData)
            ?.serverInfo?.schemaVersion
        return SendspinEncryptionMode.resolve(
            schemaVersion = schemaVersion,
            requireEncryption = settings.sendspinRequireEncryption.value,
        )
    }

    // Long-lived scope for hot-tunable settings observers. Cancelled only when the
    // shared pipeline is destroyed (user logout / permanent error).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // One load, shared by every client: the identity must be stable across reconnects.
    private val trustStoreDeferred: Deferred<SendspinTrustStore> =
        scope.async(start = CoroutineStart.LAZY) {
            SendspinTrustStore.load(keyStore, noiseCrypto)
        }

    // Shared audio pipeline — persists across SendspinClient reconnections
    private var sharedClockSynchronizer: ClockSynchronizer? = null
    private var sharedPipeline: AudioStreamManager? = null
    private var delayCollectorJob: Job? = null

    // The WebRTC sendspin channel is single-use: once a session has run on it, the
    // server side is gone even though the channel stays Open at the WebRTC layer.
    private val channelGate = WebRTCChannelGate()

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
        // The channel gate is deliberately NOT reset: tearing down the pipeline
        // doesn't revive the used sendspin channel. Only a new peer connection
        // (a new wrapper instance) makes a channel fresh again.
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

        // Resolving the protocol gate from a transient session state could
        // silently downgrade an encrypted player to cleartext; fail retryably
        // instead of guessing.
        if (serviceClient.sessionState.value !is HasConnectionData) {
            return Result.failure(
                IllegalStateException("MA session not established — deferring Sendspin start"),
            )
        }

        // Refuse before constructing any transport.
        val encryptionMode = resolveEncryptionMode()
        if (encryptionMode == SendspinEncryptionMode.ENCRYPTED_REQUIRED) {
            log.w { "Encryption required but the server does not support it — not connecting" }
            return Result.failure(EncryptionRequiredUnavailable())
        }

        // Detect connection type: WebRTC or WebSocket
        val webrtcChannel = serviceClient.webrtcSendspinChannel

        // Get or create shared pipeline — persists across reconnections
        val (pipeline, clockSync) = getOrCreatePipeline()

        return try {
            if (webrtcChannel != null) {
                createWebRTCClient(webrtcChannel, pipeline, clockSync, encryptionMode)
            } else {
                createWebSocketClient(mainConnection, authToken, pipeline, clockSync, encryptionMode)
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to create and start Sendspin client" }
            Result.failure(e)
        }
    }

    private suspend fun newClient(
        config: SendspinConfig,
        pipeline: AudioStreamManager,
        clockSync: ClockSynchronizer,
    ): SendspinClient {
        val encrypted = config.encryptionMode == SendspinEncryptionMode.ENCRYPTED
        val store = if (encrypted) trustStoreDeferred.await() else null
        // The server registers the player under the encrypted client_id (device
        // public key) on encrypted connections, and under the legacy UUID otherwise.
        settings.setSendspinEffectivePlayerId(store?.clientId ?: config.clientId)
        val client = SendspinClient(
            config = config,
            mediaPlayerController = mediaPlayerController,
            audioPipeline = pipeline,
            clockSynchronizer = clockSync,
            networkAvailable = networkMonitor.isAvailable,
            trustStore = store,
            noiseCrypto = if (encrypted) noiseCrypto else null,
        )
        if (encrypted && store != null) {
            val coordinator = SilentPairingCoordinator(
                sendRequest = serviceClient::sendRequest,
                pairingToken = store::pairingToken,
                scope = scope,
            )
            client.sessionEventListener = coordinator::onSessionEvent
        }
        return client
    }

    private suspend fun createWebRTCClient(
        webrtcChannel: DataChannelWrapper,
        pipeline: AudioStreamManager,
        clockSync: ClockSynchronizer,
        encryptionMode: SendspinEncryptionMode,
    ): Result<SendspinClient> {
        // A new wrapper instance means a new peer connection; reusing an exhausted
        // one hits a zombie channel (Open client-side, dead server-side).
        if (!channelGate.isFresh(webrtcChannel)) {
            log.i { "Sendspin channel exhausted — need WebRTC reconnect" }
            return Result.failure(WebRTCSendspinChannelExhausted())
        }

        log.i { "Creating Sendspin client over WebRTC data channel" }

        // Reserve the channel before attaching: from the first outbound frame it
        // is no longer safe to reuse, and a cancellation mid-attach must not
        // leave it reported fresh.
        channelGate.markUsed(webrtcChannel)

        // WebRTC SCTP can deliver out-of-order — reorder buffer covers it.
        // 8 frames (~160 ms) is plenty for LAN-class SCTP; the previous 32 added ~640 ms-
        // of group-sync lag. Raise if audible glitches appear on noisier links.
        pipeline.reorderDepth = 8

        val config = SendspinConfig(
            clientId = settings.sendspinClientId.value,
            deviceName = settings.sendspinDeviceName.value,
            codecPreference = settings.sendspinCodecPreference.value,
            bufferCapacityBytes = settings.sendspinBufferCapacityMb.value * SendspinConfig.BYTES_PER_MB,
            // WebRTC: auth inherited from ma-api channel, no server connection needed
            serverHost = "",
            serverPort = 0,
            mainConnectionPort = null,
            authToken = null,
            encryptionMode = encryptionMode,
        )

        val client = newClient(config, pipeline, clockSync)
        val transport = WebRTCDataChannelTransport(webrtcChannel)
        client.connectWithTransport(transport)

        // `connectWithTransport` returning proves nothing about whether the channel
        // usably carried a handshake; only the initial outcome does.
        return when (val outcome = client.awaitInitialOutcome()) {
            is SessionOutcome.Ready -> {
                log.i { "Sendspin client ready via WebRTC (auth inherited, shared pipeline)" }
                Result.success(client)
            }

            is SessionOutcome.Failed -> {
                log.w(outcome.cause) { "Sendspin WebRTC attach failed — channel exhausted" }
                // Full teardown so a late-completing handshake can't leave a zombie
                // session buffering inbound frames.
                client.stop(GoodbyeReason.Shutdown)
                client.close()
                Result.failure(WebRTCSendspinChannelExhausted())
            }
        }
    }

    private suspend fun createWebSocketClient(
        mainConnection: ConnectionInfo?,
        authToken: String?,
        pipeline: AudioStreamManager,
        clockSync: ClockSynchronizer,
        encryptionMode: SendspinEncryptionMode,
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
            encryptionMode = encryptionMode,
        )

        // WebSocket over TCP is ordered — minimal reorder buffer, just scheduling jitter
        pipeline.reorderDepth = 2

        log.i {
            "Creating Sendspin client over WebSocket (${if (config.requiresAuth) "proxy" else "custom"} mode, shared pipeline)"
        }
        val client = newClient(config, pipeline, clockSync)
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
        encryptionMode: SendspinEncryptionMode,
    ): SendspinConfig {
        val useCustomConnection = settings.sendspinUseCustomConnection.value

        return if (useCustomConnection) {
            // Custom connection mode: use separate Sendspin settings
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                codecPreference = settings.sendspinCodecPreference.value,
                bufferCapacityBytes = settings.sendspinBufferCapacityMb.value * SendspinConfig.BYTES_PER_MB,
                serverHost = settings.sendspinHost.value.takeIf { it.isNotEmpty() } ?: serverHost,
                serverPort = settings.sendspinPort.value,
                serverPath = settings.sendspinPath.value,
                useTls = settings.sendspinUseTls.value,
                useCustomConnection = true,
                authToken = authToken,
                mainConnectionPort = mainConnection.port,
                encryptionMode = encryptionMode,
            )
        } else {
            // Proxy mode: use main connection settings with /sendspin path
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                codecPreference = settings.sendspinCodecPreference.value,
                bufferCapacityBytes = settings.sendspinBufferCapacityMb.value * SendspinConfig.BYTES_PER_MB,
                serverHost = serverHost,
                serverPort = mainConnection.port,
                // Same reverse proxy as the control socket, so it needs the same base path.
                serverPath = "${mainConnection.basePath}/sendspin",
                useTls = mainConnection.isTls,
                useCustomConnection = false,
                authToken = authToken,
                mainConnectionPort = mainConnection.port,
                encryptionMode = encryptionMode,
            )
        }
    }
}
