package io.music_assistant.client.utils

import io.music_assistant.client.data.model.server.ServerInfo

sealed interface DataConnectionState {
    object AwaitingServerInfo : DataConnectionState
    data class AwaitingAuth(val authProcessState: AuthProcessState, val serverInfo: ServerInfo) :
        DataConnectionState

    data class Authenticated(val serverInfo: ServerInfo) : DataConnectionState
}
