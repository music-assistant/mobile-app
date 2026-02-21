package io.music_assistant.client.settings

import io.music_assistant.client.api.ConnectionInfo
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionHistoryEntry(
    val type: ConnectionType,
    val host: String? = null,
    val port: Int? = null,
    val isTls: Boolean? = null,
    val remoteId: String? = null,
    val lastUsedAt: Long = 0L,
) {
    val connectionInfo: ConnectionInfo?
        get() = if (type == ConnectionType.DIRECT && host != null && port != null)
            ConnectionInfo(host, port, isTls ?: false) else null

    val serverIdentifier: String
        get() = when (type) {
            ConnectionType.DIRECT -> "direct:${if (isTls == true) "wss" else "ws"}://$host:$port"
            ConnectionType.WEBRTC -> "webrtc:$remoteId"
        }

    val displayAddress: String
        get() = when (type) {
            ConnectionType.DIRECT -> "${if (isTls == true) "wss" else "ws"}://$host:$port"
            ConnectionType.WEBRTC -> remoteId ?: ""
        }
}

@Serializable
enum class ConnectionType { DIRECT, WEBRTC }
