package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.Codec
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SendspinConfig(
    val clientId: String,
    val deviceName: String,
    val enabled: Boolean = true,
    val codecPreference: Codec,
    // Advertised buffer_capacity (bytes) — user-configurable; see companion for limits.
    val bufferCapacityBytes: Int = DEFAULT_BUFFER_CAPACITY_BYTES,

    // Server connection settings
    val serverHost: String = "",
    val serverPort: Int = 8095,
    val serverPath: String = "/sendspin",
    val useTls: Boolean = false,

    // Custom connection mode
    val useCustomConnection: Boolean = false,

    // Auth settings (for proxy mode)
    val authToken: String? = null,
    val mainConnectionPort: Int? = null,

    // Protocol selection; resolved by the factory from the authenticated MA
    // session's schema version plus the require-encryption setting.
    val encryptionMode: SendspinEncryptionMode = SendspinEncryptionMode.LEGACY,
) {
    fun buildServerUrl(): String {
        return if (serverHost.isNotEmpty()) {
            val protocol = if (useTls) "wss" else "ws"
            "$protocol://$serverHost:$serverPort$serverPath"
        } else {
            ""
        }
    }

    // Proxy mode detection: if port matches main connection port, we're using the proxy
    val requiresAuth: Boolean
        get() = mainConnectionPort != null && serverPort == mainConnectionPort

    val isValid: Boolean
        get() = enabled && serverHost.isNotEmpty() && deviceName.isNotEmpty()

    companion object {
        // Advertised to the server in client/hello as `buffer_capacity` (Sendspin spec): a HARD
        // per-player limit, in BYTES, on queued audio not yet played. Byte-bounding caps memory
        // uniformly across codecs — the buffered *time* it buys then varies with bitrate (many
        // minutes for compressed, a few minutes of CD PCM, down to tens of seconds for hi-res PCM).
        // User-configurable via the Local Player settings slider (MB); these are its limits.
        const val BYTES_PER_MB: Int = 1_000_000
        const val BUFFER_MB_MIN: Int = 5
        const val BUFFER_MB_MAX: Int = 50
        const val BUFFER_MB_STEP: Int = 5
        const val BUFFER_MB_DEFAULT: Int = 15
        const val DEFAULT_BUFFER_CAPACITY_BYTES: Int = BUFFER_MB_DEFAULT * BYTES_PER_MB
    }
}
