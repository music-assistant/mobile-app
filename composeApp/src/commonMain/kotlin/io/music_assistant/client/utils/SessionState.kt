package io.music_assistant.client.utils

import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.webrtc.model.RemoteId

sealed class SessionState {
    /**
     * Connected to Music Assistant server.
     * Sealed class with Direct (WebSocket) and WebRTC subclasses.
     */
    sealed class Connected : SessionState(), HasConnectionData {
        /**
         * Connected via direct WebSocket connection (host:port).
         */
        data class Direct(
            val connectionInfo: ConnectionInfo,
            override val connectionData: ConnectionData = ConnectionData(),
        ) : Connected()

        /**
         * Connected via WebRTC peer-to-peer connection (Remote ID).
         */
        data class WebRTC(
            val remoteId: RemoteId,
            override val connectionData: ConnectionData = ConnectionData(),
        ) : Connected()
    }

    data object Connecting : SessionState()

    /**
     * Reconnecting to Music Assistant server.
     * Sealed class with Direct (WebSocket) and WebRTC subclasses.
     */
    sealed class Reconnecting : SessionState(), HasConnectionData {
        abstract val attempt: Int

        /**
         * Reconnecting via direct WebSocket connection.
         */
        data class Direct(
            override val attempt: Int,
            val connectionInfo: ConnectionInfo,
            override val connectionData: ConnectionData = ConnectionData(),
        ) : Reconnecting()

        /**
         * Reconnecting via WebRTC connection.
         */
        data class WebRTC(
            override val attempt: Int,
            val remoteId: RemoteId,
            override val connectionData: ConnectionData = ConnectionData(),
        ) : Reconnecting()
    }

    sealed class Disconnected : SessionState() {
        data object Initial : Disconnected()
        data object ByUser : Disconnected()
        data object NoServerData : Disconnected()
        data object Backgrounded : Disconnected()
        data class Error(val reason: Exception?) : Disconnected()
    }
}

/**
 * Helper extension to update ConnectionData on Connected instances.
 * Works for both Direct and WebRTC subclasses.
 */
fun SessionState.Connected.update(
    connectionData: ConnectionData = this.connectionData,
): SessionState.Connected = when (this) {
    is SessionState.Connected.Direct -> copy(connectionData = connectionData)
    is SessionState.Connected.WebRTC -> copy(connectionData = connectionData)
}

/**
 * Convenience overload preserving existing call-site syntax.
 */
fun SessionState.Connected.update(
    serverInfo: ServerInfo? = this.serverInfo,
    user: User? = this.user,
    authProcessState: AuthProcessState = this.authProcessState,
    wasAutoLogin: Boolean = this.wasAutoLogin,
    needsServerReauth: Boolean = this.connectionData.needsServerReauth,
): SessionState.Connected = update(
    connectionData = ConnectionData(serverInfo, user, authProcessState, wasAutoLogin, needsServerReauth),
)

/**
 * Helper extension to get connectionInfo from any Connected instance.
 * Returns ConnectionInfo for Direct, null for WebRTC.
 */
val SessionState.Connected.connectionInfo: ConnectionInfo?
    get() = when (this) {
        is SessionState.Connected.Direct -> connectionInfo
        is SessionState.Connected.WebRTC -> null
    }

/**
 * Helper extension to update ConnectionData on Reconnecting instances.
 */
fun SessionState.Reconnecting.update(
    connectionData: ConnectionData = this.connectionData,
): SessionState.Reconnecting = when (this) {
    is SessionState.Reconnecting.Direct -> copy(connectionData = connectionData)
    is SessionState.Reconnecting.WebRTC -> copy(connectionData = connectionData)
}

/**
 * Convenience overload preserving existing call-site syntax.
 */
fun SessionState.Reconnecting.update(
    serverInfo: ServerInfo? = this.serverInfo,
    user: User? = this.user,
    authProcessState: AuthProcessState = this.authProcessState,
    wasAutoLogin: Boolean = this.wasAutoLogin,
    needsServerReauth: Boolean = this.connectionData.needsServerReauth,
): SessionState.Reconnecting = update(
    connectionData = ConnectionData(serverInfo, user, authProcessState, wasAutoLogin, needsServerReauth),
)

/**
 * Helper extension to get connectionInfo from any Reconnecting instance.
 */
val SessionState.Reconnecting.connectionInfo: ConnectionInfo?
    get() = when (this) {
        is SessionState.Reconnecting.Direct -> connectionInfo
        is SessionState.Reconnecting.WebRTC -> null
    }
