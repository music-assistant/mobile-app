package io.music_assistant.client.utils

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.sendSerialized
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.serialization.json.JsonObject

sealed class SessionState {
    /**
     * Connected to Music Assistant server.
     * Sealed class with Direct (WebSocket) and WebRTC subclasses.
     */
    sealed class Connected : SessionState() {
        abstract val serverInfo: ServerInfo?
        abstract val user: User?
        abstract val authProcessState: AuthProcessState
        abstract val wasAutoLogin: Boolean

        val dataConnectionState: DataConnectionState
            get() = when {
                serverInfo == null -> DataConnectionState.AwaitingServerInfo
                user == null -> DataConnectionState.AwaitingAuth(authProcessState)
                else -> DataConnectionState.Authenticated
            }

        /**
         * Connected via direct WebSocket connection (host:port).
         */
        data class Direct(
            val session: DefaultClientWebSocketSession,
            val connectionInfo: ConnectionInfo,
            override val serverInfo: ServerInfo? = null,
            override val user: User? = null,
            override val authProcessState: AuthProcessState = AuthProcessState.NotStarted,
            override val wasAutoLogin: Boolean = false,
        ) : Connected()

        /**
         * Connected via WebRTC peer-to-peer connection (Remote ID).
         */
        data class WebRTC(
            val manager: WebRTCConnectionManager,
            val remoteId: RemoteId,
            override val serverInfo: ServerInfo? = null,
            override val user: User? = null,
            override val authProcessState: AuthProcessState = AuthProcessState.NotStarted,
            override val wasAutoLogin: Boolean = false,
        ) : Connected()
    }

    data object Connecting : SessionState()

    /**
     * Reconnecting to Music Assistant server.
     * Sealed class with Direct (WebSocket) and WebRTC subclasses.
     */
    sealed class Reconnecting : SessionState() {
        abstract val attempt: Int
        abstract val serverInfo: ServerInfo?
        abstract val user: User?
        abstract val authProcessState: AuthProcessState
        abstract val wasAutoLogin: Boolean

        val dataConnectionState: DataConnectionState
            get() = when {
                serverInfo == null -> DataConnectionState.AwaitingServerInfo
                user == null -> DataConnectionState.AwaitingAuth(authProcessState)
                else -> DataConnectionState.Authenticated
            }

        /**
         * Reconnecting via direct WebSocket connection.
         */
        data class Direct(
            override val attempt: Int,
            val connectionInfo: ConnectionInfo,
            override val serverInfo: ServerInfo? = null,
            override val user: User? = null,
            override val authProcessState: AuthProcessState = AuthProcessState.NotStarted,
            override val wasAutoLogin: Boolean = false,
        ) : Reconnecting()

        /**
         * Reconnecting via WebRTC connection.
         */
        data class WebRTC(
            override val attempt: Int,
            val remoteId: RemoteId,
            override val serverInfo: ServerInfo? = null,
            override val user: User? = null,
            override val authProcessState: AuthProcessState = AuthProcessState.NotStarted,
            override val wasAutoLogin: Boolean = false,
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
 * Helper extension to update common fields on Connected instances.
 * Works for both Direct and WebRTC subclasses.
 */
fun SessionState.Connected.update(
    serverInfo: ServerInfo? = this.serverInfo,
    user: User? = this.user,
    authProcessState: AuthProcessState = this.authProcessState,
    wasAutoLogin: Boolean = this.wasAutoLogin
): SessionState.Connected = when (this) {
    is SessionState.Connected.Direct -> copy(
        serverInfo = serverInfo,
        user = user,
        authProcessState = authProcessState,
        wasAutoLogin = wasAutoLogin
    )

    is SessionState.Connected.WebRTC -> copy(
        serverInfo = serverInfo,
        user = user,
        authProcessState = authProcessState,
        wasAutoLogin = wasAutoLogin
    )
}

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
 * Helper extension to get WebSocket session from Connected.Direct.
 * Returns null for WebRTC connections.
 */
val SessionState.Connected.session: DefaultClientWebSocketSession?
    get() = when (this) {
        is SessionState.Connected.Direct -> this.session
        is SessionState.Connected.WebRTC -> null
    }

/**
 * Helper extension to update common fields on Reconnecting instances.
 */
fun SessionState.Reconnecting.update(
    serverInfo: ServerInfo? = this.serverInfo,
    user: User? = this.user,
    authProcessState: AuthProcessState = this.authProcessState,
    wasAutoLogin: Boolean = this.wasAutoLogin
): SessionState.Reconnecting = when (this) {
    is SessionState.Reconnecting.Direct -> copy(
        serverInfo = serverInfo,
        user = user,
        authProcessState = authProcessState,
        wasAutoLogin = wasAutoLogin
    )

    is SessionState.Reconnecting.WebRTC -> copy(
        serverInfo = serverInfo,
        user = user,
        authProcessState = authProcessState,
        wasAutoLogin = wasAutoLogin
    )
}

/**
 * Helper extension to get connectionInfo from any Reconnecting instance.
 */
val SessionState.Reconnecting.connectionInfo: ConnectionInfo?
    get() = when (this) {
        is SessionState.Reconnecting.Direct -> connectionInfo
        is SessionState.Reconnecting.WebRTC -> null
    }

/**
 * Send a JSON message through the appropriate transport (WebSocket or WebRTC).
 * Abstracts away the transport-specific send logic.
 */
suspend fun SessionState.Connected.sendMessage(message: JsonObject) {
    when (this) {
        is SessionState.Connected.Direct -> {
            session.sendSerialized(message)
        }

        is SessionState.Connected.WebRTC -> {
            val json = myJson.encodeToString(JsonObject.serializer(), message)
            manager.send(json)
        }
    }
}
