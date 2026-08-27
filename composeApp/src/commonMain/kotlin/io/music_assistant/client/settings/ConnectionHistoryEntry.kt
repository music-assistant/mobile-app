package io.music_assistant.client.settings

import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.utils.SessionState
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionHistoryEntry(
    val type: ConnectionType,
    val host: String? = null,
    val port: Int? = null,
    val isTls: Boolean? = null,
    val basePath: String = "",
    val remoteId: String? = null,
    val lastUsedAt: Long = 0L,
    val serverName: String? = null,
    val serverId: String? = null,
) {
    val connectionInfo: ConnectionInfo?
        get() = if (type == ConnectionType.DIRECT && host != null && port != null) {
            ConnectionInfo(host, port, isTls ?: false, basePath)
        } else {
            null
        }

    /** Where this server was reached. Not an identity: one address can host many servers. */
    val serverIdentifier: String
        get() = when (type) {
            ConnectionType.DIRECT ->
                "direct:${if (isTls == true) "wss" else "ws"}://$host:$port$basePath"
            ConnectionType.WEBRTC -> "webrtc:$remoteId"
        }

    /** History identity: the same address can host different servers over time. */
    val historyKey: String
        get() = "$serverIdentifier|${serverId.orEmpty()}"

    /** Enough of the server id to tell two servers on one address apart. */
    val shortServerId: String?
        get() = serverId?.take(SHORT_SERVER_ID_LENGTH)

    val displayAddress: String
        get() = when (type) {
            ConnectionType.DIRECT -> "${if (isTls == true) "wss" else "ws"}://$host:$port$basePath"
            ConnectionType.WEBRTC -> remoteId.orEmpty()
        }

    companion object {
        private const val SHORT_SERVER_ID_LENGTH = 8

        /** The single place a history entry is built from a live, identified connection. */
        fun from(state: SessionState.Connected, serverInfo: ServerInfo): ConnectionHistoryEntry =
            when (state) {
                is SessionState.Connected.Direct -> ConnectionHistoryEntry(
                    type = ConnectionType.DIRECT,
                    host = state.connectionInfo.host,
                    port = state.connectionInfo.port,
                    isTls = state.connectionInfo.isTls,
                    basePath = state.connectionInfo.basePath,
                    serverName = serverInfo.name?.takeIf { it.isNotBlank() },
                    serverId = serverInfo.serverId,
                )

                is SessionState.Connected.WebRTC -> ConnectionHistoryEntry(
                    type = ConnectionType.WEBRTC,
                    remoteId = state.remoteId.rawId,
                    serverName = serverInfo.name?.takeIf { it.isNotBlank() },
                    serverId = serverInfo.serverId,
                )
            }
    }
}

@Serializable
enum class ConnectionType { DIRECT, WEBRTC }
