package io.music_assistant.client.connection

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.connectionInfo
import io.music_assistant.client.utils.mainDispatcher
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionManager(
    private val serviceClient: ServiceClient,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    val serverBaseUrl: StateFlow<String?> = serviceClient.sessionState
        .map { state ->
            when (state) {
                is SessionState.Connected.Direct -> state.connectionInfo.webUrl
                is SessionState.Reconnecting.Direct -> state.connectionInfo.webUrl
                else -> null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            serviceClient.sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(connInfo)
                        }
                    }

                    is SessionState.Reconnecting -> {
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(connInfo)
                        }
                    }

                    is SessionState.Disconnected -> {
                        when (state) {
                            SessionState.Disconnected.ByUser,
                            SessionState.Disconnected.NoServerData,
                            SessionState.Disconnected.Backgrounded,
                            is SessionState.Disconnected.Error,
                                -> Unit

                            SessionState.Disconnected.Initial -> {
                                val mostRecent = settings.connectionHistory.value.firstOrNull()
                                when (mostRecent?.type) {
                                    ConnectionType.DIRECT -> {
                                        val connInfo = mostRecent.connectionInfo
                                        if (connInfo != null) {
                                            serviceClient.connect(connInfo)
                                        } else {
                                            serviceClient.noServer()
                                        }
                                    }

                                    ConnectionType.WEBRTC -> {
                                        val remoteId =
                                            mostRecent.remoteId?.let { RemoteId.parse(it) }
                                        if (remoteId != null) {
                                            serviceClient.connectWebRTC(remoteId)
                                        } else {
                                            serviceClient.noServer()
                                        }
                                    }

                                    else -> {
                                        settings.connectionInfo.value?.let {
                                            serviceClient.connect(it)
                                        } ?: serviceClient.noServer()
                                    }
                                }
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
