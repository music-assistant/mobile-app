package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.ktor.http.Url
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.settings.SettingsRepository

/**
 * Factory for creating SendspinClient instances with proper configuration.
 * Separates client creation logic from lifecycle management.
 */
class SendspinClientFactory(
    private val settings: SettingsRepository,
    private val mediaPlayerController: MediaPlayerController
) {
    private val log = Logger.withTag("SendspinClientFactory")

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

        log.i { "Creating Sendspin client: $serverHost:${config.serverPort} (${if (config.requiresAuth) "proxy" else "custom"} mode)" }

        // Create client
        return try {
            val client = SendspinClient(config, mediaPlayerController)
            Result.success(client)
        } catch (e: Exception) {
            log.e(e) { "Failed to create Sendspin client" }
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
