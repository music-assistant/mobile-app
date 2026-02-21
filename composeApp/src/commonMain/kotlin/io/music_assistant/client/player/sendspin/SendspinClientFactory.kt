package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.ktor.http.Url
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.audio.AudioStreamManager
import io.music_assistant.client.player.sendspin.transport.WebRTCDataChannelTransport
import io.music_assistant.client.settings.SettingsRepository

/**
 * Factory for creating SendspinClient instances with proper configuration.
 * Separates client creation logic from lifecycle management.
 * Automatically detects WebRTC vs WebSocket connection and uses appropriate transport.
 *
 * Owns a shared [AudioStreamManager] + [ClockSynchronizer] that persist across reconnections,
 * so the audio sink keeps playing from its buffer while the protocol layer reconnects.
 */
class SendspinClientFactory(
    private val settings: SettingsRepository,
    private val mediaPlayerController: MediaPlayerController,
    private val serviceClient: ServiceClient
) {
    private val log = Logger.withTag("SendspinClientFactory")

    // Shared audio pipeline — persists across SendspinClient reconnections
    private var sharedClockSynchronizer: ClockSynchronizer? = null
    private var sharedPipeline: AudioStreamManager? = null

    /**
     * Returns the shared pipeline (and its clock synchronizer), creating them if needed.
     * Both are passed to new SendspinClient instances so the audio sink persists across reconnects.
     */
    fun getOrCreatePipeline(): Pair<AudioStreamManager, ClockSynchronizer> {
        val cs = sharedClockSynchronizer ?: ClockSynchronizer().also { sharedClockSynchronizer = it }
        val pipeline = sharedPipeline ?: AudioStreamManager(cs, mediaPlayerController).also { sharedPipeline = it }
        return Pair(pipeline, cs)
    }

    /**
     * Fully destroys the shared pipeline (called on user logout or persistent error).
     * Next createIfEnabled() will allocate a fresh pipeline.
     */
    suspend fun destroyPipeline() {
        log.i { "Destroying shared audio pipeline" }
        sharedPipeline?.stopStream()
        sharedPipeline?.close()
        sharedPipeline = null
        sharedClockSynchronizer = null
    }

    /**
     * Creates a SendspinClient if enabled and all prerequisites are met.
     *
     * @param mainConnection The main Music Assistant connection info (for server host and proxy detection)
     * @param authToken User authentication token (required for Sendspin)
     * @return Result containing SendspinClient on success, or error message on failure
     */
    suspend fun createIfEnabled(
        mainConnection: ConnectionInfo,
        authToken: String?
    ): Result<SendspinClient> {
        // Validate: Sendspin enabled
        if (!settings.sendspinEnabled.value) {
            return Result.failure(
                IllegalStateException("Sendspin disabled in settings")
            )
        }

        // Validate: Auth token required
        if (authToken == null) {
            return Result.failure(
                IllegalStateException("No auth token available - user must be logged in")
            )
        }

        // Extract server host from main connection URL
        val serverHost = try {
            Url(mainConnection.webUrl).host
        } catch (e: Exception) {
            log.e(e) { "Failed to parse server URL: ${mainConnection.webUrl}" }
            return Result.failure(
                IllegalArgumentException("Invalid server URL: ${mainConnection.webUrl}", e)
            )
        }

        // Build Sendspin configuration based on connection mode
        val config = buildConfig(
            serverHost = serverHost,
            mainConnection = mainConnection,
            authToken = authToken
        )

        // Validate device name (required for protocol)
        if (config.deviceName.isBlank()) {
            return Result.failure(
                IllegalStateException("Sendspin device name cannot be empty")
            )
        }

        // Detect connection type: WebRTC or WebSocket
        val webrtcChannel = serviceClient.webrtcSendspinChannel

        // Get or create shared pipeline — persists across reconnections
        val (pipeline, clockSync) = getOrCreatePipeline()

        return try {
            if (webrtcChannel != null) {
                // WebRTC mode: create config WITHOUT auth requirement (auth inherited from main channel)
                log.i { "Creating Sendspin client over WebRTC data channel" }

                val webrtcConfig = config.copy(
                    // Override auth settings for WebRTC - auth is inherited from main channel
                    serverPort = 0,  // Not used for WebRTC
                    mainConnectionPort = null,  // This makes requiresAuth = false
                    authToken = null  // Not needed, auth already done on ma-api channel
                )

                val client = SendspinClient(
                    config = webrtcConfig,
                    mediaPlayerController = mediaPlayerController,
                    externalPipeline = pipeline,
                    externalClockSynchronizer = clockSync
                )
                val transport = WebRTCDataChannelTransport(webrtcChannel)
                client.connectWithTransport(transport)
                log.i { "Sendspin client connected via WebRTC (auth inherited, direct hello, shared pipeline)" }
                Result.success(client)
            } else {
                // WebSocket mode: use standard WebSocket transport
                log.i { "Creating Sendspin client over WebSocket: $serverHost:${config.serverPort} (${if (config.requiresAuth) "proxy" else "custom"} mode, shared pipeline)" }
                val client = SendspinClient(
                    config = config,
                    mediaPlayerController = mediaPlayerController,
                    externalPipeline = pipeline,
                    externalClockSynchronizer = clockSync
                )
                client.start()
                log.i { "Sendspin client started via WebSocket" }
                Result.success(client)
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to create and start Sendspin client" }
            Result.failure(e)
        }
    }

    /**
     * Builds SendspinConfig based on user settings and connection mode.
     * Supports both proxy mode (default) and custom connection mode.
     */
    private fun buildConfig(
        serverHost: String,
        mainConnection: ConnectionInfo,
        authToken: String
    ): SendspinConfig {
        val useCustomConnection = settings.sendspinUseCustomConnection.value

        return if (useCustomConnection) {
            // Custom connection mode: use separate Sendspin settings
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                bufferCapacityMicros = 500_000, // 500ms
                codecPreference = settings.sendspinCodecPreference.value,
                serverHost = settings.sendspinHost.value.takeIf { it.isNotEmpty() } ?: serverHost,
                serverPort = settings.sendspinPort.value,
                serverPath = settings.sendspinPath.value,
                useTls = settings.sendspinUseTls.value,
                useCustomConnection = true,
                authToken = authToken,
                mainConnectionPort = mainConnection.port
            )
        } else {
            // Proxy mode: use main connection settings with /sendspin path
            SendspinConfig(
                clientId = settings.sendspinClientId.value,
                deviceName = settings.sendspinDeviceName.value,
                enabled = true,
                bufferCapacityMicros = 500_000, // 500ms
                codecPreference = settings.sendspinCodecPreference.value,
                serverHost = serverHost,
                serverPort = mainConnection.port,
                serverPath = "/sendspin",
                useTls = mainConnection.isTls,
                useCustomConnection = false,
                authToken = authToken,
                mainConnectionPort = mainConnection.port
            )
        }
    }
}
