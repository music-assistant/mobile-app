package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import com.sendspin.protocol.AudioFormat
import com.sendspin.protocol.ClientPreferences
import com.sendspin.protocol.ClientSettingsStore
import com.sendspin.protocol.NoOpClientSettingsStore
import com.sendspin.protocol.SendSpinTransport
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.http.Url
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.MediaPlayerAudioPlayer
import io.music_assistant.client.player.sendspin.model.DeviceInfo
import io.music_assistant.client.player.sendspin.transport.AuthenticatingTransport
import io.music_assistant.client.player.sendspin.transport.WebRTCDataChannelTransport
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import com.sendspin.protocol.ClockSync as LibraryClockSync
import com.sendspin.protocol.SendSpinClient as LibraryClient

/**
 * Signals that the WebRTC sendspin channel was already used (goodbye sent) and a full WebRTC
 * reconnection is needed to get a fresh channel.
 */
class WebRTCSendspinChannelExhausted : Exception("WebRTC sendspin channel exhausted")

/**
 * Creates configured [SendspinClient] adapters over the canonical `sendspin-kmp` library.
 *
 * The library client owns its own audio buffer + clock sync and reconnects the transport
 * internally, so a transient WebSocket drop preserves buffered audio without app involvement. Each
 * [createIfEnabled] builds a fresh library client with the appropriate transport (auth-decorated
 * WebSocket for proxy mode, plain WebSocket for custom mode, or the WebRTC data channel).
 */
class SendspinClientFactory(
    private val settings: SettingsRepository,
    private val mediaPlayerController: MediaPlayerController,
    private val serviceClient: ServiceClient,
    private val networkMonitor: NetworkMonitor,
) {
    private val log = Logger.withTag("SendspinClientFactory")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Shared HttpClient (WebSockets installed) reused across WebSocket (re)connections.
    private val httpClient by lazy { createPlatformHttpClient { install(WebSockets) { pingInterval = 5.seconds } } }

    // The persistent library client. It survives a warm Restart (transport disconnected, buffer +
    // player kept draining) so the next createIfEnabled reuses it — preserving buffered audio across
    // the reinit. Reused only when the connection key matches; otherwise torn down and rebuilt.
    private var persistentClient: LibraryClient? = null
    private var persistentPlayer: MediaPlayerAudioPlayer? = null
    private var persistentKey: String? = null
    private var delayCollectorJob: Job? = null

    // WebRTC sendspin channel is single-use (see createWebRTCClient). The DataChannelWrapper
    // instance is the "channel freshness" identity.
    private var lastObservedChannel: DataChannelWrapper? = null
    private var webrtcSendspinUsed = false

    /** The current library client's clock, exposed so the owner can reset it on foreground. */
    fun currentClockSynchronizer(): LibraryClockSync? = persistentClient?.clockSync

    /** Fully tears down the persistent client + pipeline and stops delay tuning. */
    suspend fun destroyPipeline() {
        log.i { "Destroying Sendspin pipeline" }
        destroyPersistent()
        // Channel-freshness state is intentionally NOT reset — see createWebRTCClient. A goodbye we
        // already shipped on the existing channel means the server handler is gone; only a new
        // DataChannelWrapper instance (fresh peer connection) may reuse the sendspin role.
    }

    private fun destroyPersistent() {
        delayCollectorJob?.cancel()
        delayCollectorJob = null
        persistentClient?.close()
        persistentPlayer?.close()
        persistentClient = null
        persistentPlayer = null
        persistentKey = null
    }

    suspend fun createIfEnabled(
        mainConnection: ConnectionInfo?,
        authToken: String?,
    ): Result<SendspinClient> {
        if (!settings.sendspinEnabled.value) {
            return Result.failure(IllegalStateException("Sendspin disabled in settings"))
        }
        if (settings.sendspinDeviceName.value.isBlank()) {
            return Result.failure(IllegalStateException("Sendspin device name cannot be empty"))
        }

        val webrtcChannel = serviceClient.webrtcSendspinChannel
        return try {
            if (webrtcChannel != null) {
                createWebRTCClient(webrtcChannel)
            } else {
                createWebSocketClient(mainConnection, authToken)
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to create Sendspin client" }
            Result.failure(e)
        }
    }

    private suspend fun createWebRTCClient(webrtcChannel: DataChannelWrapper): Result<SendspinClient> {
        // Identity-based freshness: a different wrapper means a new peer connection, so the channel
        // has never sent goodbye and is safe to use. Reusing the same instance after goodbye hits a
        // zombie channel (Open client-side, dead server-side) — refuse and force a WebRTC reconnect.
        if (lastObservedChannel !== webrtcChannel) {
            webrtcSendspinUsed = false
            lastObservedChannel = webrtcChannel
        }
        if (webrtcSendspinUsed) {
            log.i { "Sendspin channel exhausted — need WebRTC reconnect" }
            return Result.failure(WebRTCSendspinChannelExhausted())
        }

        log.i { "Creating Sendspin client over WebRTC data channel" }
        val config = SendspinConfig(
            clientId = settings.sendspinClientId.value,
            deviceName = settings.sendspinDeviceName.value,
            codecPreference = settings.sendspinCodecPreference.value,
            serverHost = "",
            serverPort = 0,
            mainConnectionPort = null,
            authToken = null,
        )
        // WebRTC channels are single-use (exhaustion guard above), so never reuse — always fresh.
        val adapter = buildFresh(
            key = "webrtc",
            config = config,
            // WebRTC: the channel is host-owned and single-use, so the library must NOT reconnect it.
            reconnectEnabled = false,
            transportFactory = { WebRTCDataChannelTransport(webrtcChannel) },
        )
        // Mark used only after connect succeeds (a thrown attach hasn't sent goodbye, so the channel
        // is still virgin and the caller can retry without a slow WebRTC peer reconnect).
        webrtcSendspinUsed = true
        log.i { "Sendspin client connected via WebRTC (auth inherited, shared pipeline)" }
        return Result.success(adapter)
    }

    private suspend fun createWebSocketClient(
        mainConnection: ConnectionInfo?,
        authToken: String?,
    ): Result<SendspinClient> {
        if (mainConnection == null) {
            return Result.failure(IllegalStateException("No connection info available for WebSocket Sendspin"))
        }
        if (authToken == null) {
            return Result.failure(IllegalStateException("No auth token available - user must be logged in"))
        }

        val serverHost = try {
            Url(mainConnection.webUrl).host
        } catch (e: Exception) {
            log.e(e) { "Failed to parse server URL" }
            return Result.failure(IllegalArgumentException("Invalid server URL", e))
        }

        val config = buildConfig(serverHost, mainConnection, authToken)
        val url = config.buildServerUrl()
        val clientId = config.clientId
        val requiresAuth = config.requiresAuth

        log.i { "Creating Sendspin client over WebSocket (${if (requiresAuth) "proxy" else "custom"} mode)" }
        // Reuse the persistent client on a warm Restart (same server/auth) to preserve buffered audio.
        val key = "ws|$url|${authToken.hashCode()}|$requiresAuth"
        val adapter = reuseOrBuild(
            key = key,
            config = config,
            reconnectEnabled = true,
            transportFactory = {
                val ws: SendSpinTransport = com.sendspin.protocol.KtorWebSocketTransport(httpClient, url, Dispatchers.Default)
                if (requiresAuth && authToken != null) {
                    AuthenticatingTransport(ws, authToken, clientId)
                } else {
                    ws
                }
            },
        )
        log.i { "Sendspin client started via WebSocket" }
        return Result.success(adapter)
    }

    /** Reuse the persistent client if [key] matches (reconnecting it); otherwise build fresh. */
    private fun reuseOrBuild(
        key: String,
        config: SendspinConfig,
        reconnectEnabled: Boolean,
        transportFactory: () -> SendSpinTransport,
    ): SendspinClient {
        val existing = persistentClient
        val player = persistentPlayer
        if (existing != null && player != null && persistentKey == key) {
            log.i { "Reusing persistent Sendspin client (warm reconnect)" }
            existing.connect()
            return SendspinClient(existing, player)
        }
        return buildFresh(key, config, reconnectEnabled, transportFactory)
    }

    /**
     * Tears down any existing persistent client, constructs a fresh library client (capturing its
     * [MediaPlayerAudioPlayer]), starts it, wires the playback-delay collector, and returns the adapter.
     */
    private fun buildFresh(
        key: String,
        config: SendspinConfig,
        reconnectEnabled: Boolean,
        transportFactory: () -> SendSpinTransport,
    ): SendspinClient {
        destroyPersistent()

        var capturedPlayer: MediaPlayerAudioPlayer? = null
        val device = DeviceInfo.current
        val libraryClient = LibraryClient(
            transportFactory = transportFactory,
            preferences = buildPreferences(config),
            clientId = config.clientId,
            clientName = config.deviceName,
            manufacturer = device.manufacturer ?: "Music Assistant",
            productName = device.model ?: "Mobile Application",
            softwareVersion = device.softwareVersion ?: "1.0.0",
            audioPlayerFactory = { buffer, clock ->
                MediaPlayerAudioPlayer(clock, buffer, mediaPlayerController).also { capturedPlayer = it }
            },
            reconnectEnabled = reconnectEnabled,
            maxReconnectAttempts = MAX_RECONNECT_ATTEMPTS,
            settingsStore = StaticDelaySettingsStore(settings),
            ioContext = Dispatchers.Default,
            audioContext = io.music_assistant.client.utils.audioDispatcher,
        )

        persistentClient = libraryClient
        persistentPlayer = capturedPlayer
        persistentKey = key
        delayCollectorJob = scope.launch {
            settings.sendspinStaticDelayMs.collect { ms -> libraryClient.setStaticDelayMs(ms) }
        }

        libraryClient.connect()
        return SendspinClient(libraryClient, capturedPlayer!!)
    }

    private fun buildPreferences(config: SendspinConfig): ClientPreferences {
        val codecName = config.codecPreference.sendspinAudioCodec.name.lowercase()
        val sampleRates = listOf(44100, 48000, 88200, 96000, 192000)
        val bitDepths = listOf(16, 24, 32)
        val formats = buildList {
            for (rate in sampleRates) for (depth in bitDepths) {
                add(AudioFormat(codec = codecName, channels = 2, sampleRate = rate, bitDepth = depth))
            }
        }
        return ClientPreferences(
            supportedFormats = formats,
            artworkChannels = emptyList(),
            // Preserve the app's advertised buffer window and (empty) command set.
            playerBufferCapacity = SendspinConfig.bufferCapacityFor(config.codecPreference),
            playerSupportedCommands = emptyList(),
        )
    }

    private fun buildConfig(
        serverHost: String,
        mainConnection: ConnectionInfo,
        authToken: String,
    ): SendspinConfig {
        val useCustomConnection = settings.sendspinUseCustomConnection.value
        return if (useCustomConnection) {
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

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 6
    }
}

/** Reads the app's playback-delay setting into the library; writes are the app's job (no-op here). */
private class StaticDelaySettingsStore(
    private val settings: SettingsRepository,
) : ClientSettingsStore by NoOpClientSettingsStore {
    override fun getInt(key: String, default: Int): Int =
        if (key == com.sendspin.protocol.ClientSettingsKeys.STATIC_DELAY_MS) {
            settings.sendspinStaticDelayMs.value
        } else {
            default
        }
}
