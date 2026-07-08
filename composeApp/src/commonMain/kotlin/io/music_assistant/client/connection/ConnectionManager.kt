package io.music_assistant.client.connection

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.connectionInfo
import io.music_assistant.client.utils.mainDispatcher
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

                    else -> {}
                }
            }
        }
    }
}
