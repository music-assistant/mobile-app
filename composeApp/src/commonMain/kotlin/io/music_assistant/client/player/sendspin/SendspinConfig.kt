package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.Codec
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SendspinConfig(
    val clientId: String,
    val deviceName: String,
    val enabled: Boolean = true,
    val codecPreference: Codec,

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
        // Advertised to server in client/hello; controls how much audio it may pre-push.
        // A deep prebuffer lets network drops pass without playback hiccups, but the queue
        // holds raw frames in memory — so PCM (uncompressed) stays shallow while compressed
        // codecs get a wide window for cheap.
        const val BUFFER_CAPACITY_PCM_MICROS: Int = 10_000_000 // 10s
        const val BUFFER_CAPACITY_COMPRESSED_MICROS: Int = 30_000_000 // 30s

        fun bufferCapacityFor(codec: Codec): Int =
            if (codec == Codec.PCM) BUFFER_CAPACITY_PCM_MICROS else BUFFER_CAPACITY_COMPRESSED_MICROS
    }
}
