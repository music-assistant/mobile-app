package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.Codec
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class SendspinConfig(
    val clientId: String,
    val deviceName: String,
    val enabled: Boolean = true,
    val bufferCapacityMicros: Int = 500_000, // 500ms
    val codecPreference: Codec,

    // Server connection settings
    val serverHost: String = "", 
    val serverPort: Int = 8927, 
    val serverPath: String = "/sendspin",
    val useTls: Boolean = false
) {
    fun buildServerUrl(): String {
        return if (serverHost.isNotEmpty()) {
            val protocol = if (useTls) "wss" else "ws"
            "$protocol://$serverHost:$serverPort$serverPath"
        } else {
            ""
        }
    }

    val isValid: Boolean
        get() = enabled && serverHost.isNotEmpty() && deviceName.isNotEmpty()
}
