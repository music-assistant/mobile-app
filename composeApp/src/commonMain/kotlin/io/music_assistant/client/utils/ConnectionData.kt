package io.music_assistant.client.utils

import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User

/**
 * Shared data carried by both Connected and Reconnecting states.
 * Single source of truth for server info, user, and auth state.
 */
data class ConnectionData(
    val serverInfo: ServerInfo? = null,
    val user: User? = null,
    val authProcessState: AuthProcessState = AuthProcessState.NotStarted,
    val wasAutoLogin: Boolean = false,
) {
    val dataConnectionState: DataConnectionState
        get() = when {
            serverInfo == null -> DataConnectionState.AwaitingServerInfo
            user == null -> DataConnectionState.AwaitingAuth(authProcessState)
            else -> DataConnectionState.Authenticated
        }
}

/**
 * Interface for SessionState variants that carry connection data.
 * Implemented by Connected and Reconnecting.
 */
sealed interface HasConnectionData {
    val connectionData: ConnectionData
    val serverInfo: ServerInfo? get() = connectionData.serverInfo
    val user: User? get() = connectionData.user
    val authProcessState: AuthProcessState get() = connectionData.authProcessState
    val wasAutoLogin: Boolean get() = connectionData.wasAutoLogin
    val dataConnectionState: DataConnectionState get() = connectionData.dataConnectionState
}
