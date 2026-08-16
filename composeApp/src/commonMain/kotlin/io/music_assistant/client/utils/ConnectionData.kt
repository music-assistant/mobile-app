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
    val token: String? = null,
    /**
     * True when the transport reconnected and the underlying server-side session
     * does NOT survive that reconnect (e.g. WebRTC: every new peer connection is a
     * brand-new session on the server). The `user` field is intentionally preserved
     * so the UI keeps showing the user as logged in, but [dataConnectionState]
     * reports `AwaitingAuth` until a fresh `authorize` round-trip completes.
     * Cleared automatically by `KtorServiceClient.authorize` on success.
     */
    val needsServerReauth: Boolean = false,
) {
    val dataConnectionState: DataConnectionState
        get() = when {
            serverInfo == null -> DataConnectionState.AwaitingServerInfo
            user == null || needsServerReauth || token == null -> {
                DataConnectionState.AwaitingAuth(
                    authProcessState,
                    serverInfo,
                )
            }

            else -> DataConnectionState.Authenticated(serverInfo, token)
        }
}

/**
 * Interface for SessionState variants that carry connection data.
 * Implemented by Connected and Reconnecting.
 */
sealed interface HasConnectionData {
    val connectionData: ConnectionData
    val serverInfo: ServerInfo?
        get() = dataConnectionState.let {
            when (it) {
                is DataConnectionState.Authenticated -> it.serverInfo
                is DataConnectionState.AwaitingAuth -> it.serverInfo
                DataConnectionState.AwaitingServerInfo -> null
            }
        }

    val user: User? get() = connectionData.user
    val authProcessState: AuthProcessState get() = connectionData.authProcessState
    val wasAutoLogin: Boolean get() = connectionData.wasAutoLogin
    val dataConnectionState: DataConnectionState get() = connectionData.dataConnectionState
}
