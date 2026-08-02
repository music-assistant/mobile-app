package io.music_assistant.client.player.sendspin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The few Sendspin message/model types the app still owns after adopting the `sendspin-kmp`
 * library. The protocol's full message vocabulary now lives in the library; only these remain:
 *  - [ClientAuthMessage] — Music Assistant's proxy-mode auth frame (see AuthenticatingTransport),
 *    which the upstream protocol has no concept of.
 *  - [DeviceInfo] — client hardware description advertised via the library's client/hello.
 *  - [GoodbyeReason] — the wire reason the owner passes to SendspinClient.stop().
 */

/** Base for the app-owned client messages. */
@Serializable
sealed interface SendspinMessage {
    val type: String
}

@Serializable
data class ClientAuthMessage(
    override val type: String = "auth",
    val token: String,
    @SerialName("client_id") val clientId: String,
) : SendspinMessage

@Serializable
data class DeviceInfo(
    @SerialName("model") val model: String? = null,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("manufacturer_id") val manufacturerId: String? = null,
    @SerialName("software_version") val softwareVersion: String? = null,
) {
    companion object {
        // Platform-specific implementation needed
        val current = DeviceInfo(
            model = "Mobile Application",
            modelId = "mobile_app",
            manufacturer = "Music Assistant",
            manufacturerId = "music_assistant",
            softwareVersion = "1.0.0", // TODO: Get actual app version from build config or similar
        )
    }
}

/**
 * Wire reasons for `client/goodbye`, mirroring aiosendspin's `GoodbyeReason`.
 * The server acts on this: [Shutdown]/[UserRequest] trigger immediate session teardown, while
 * [Restart] is a warm, reconnect-friendly disconnect (30s grace) that preserves queue/resume state.
 */
enum class GoodbyeReason(val wire: String) {
    Shutdown("shutdown"),
    Restart("restart"),
    UserRequest("user_request"),
}
