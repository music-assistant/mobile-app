package io.music_assistant.client.support

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class FakeServiceClient(private val settingsRepository: SettingsRepository) : ServiceClient {

    val username = "user"
    val password = "password"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    override suspend fun sendRequest(request: Request): Result<Answer> {
        return if (request.command == Request.Auth.providers().command) {
            Result.success(
                Answer(
                    JsonObject(
                        mapOf(
                            "message_id" to JsonPrimitive(request.messageId),
                            "result" to JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "provider_id" to JsonPrimitive("builtin"),
                                            "provider_type" to JsonPrimitive("builtin"),
                                            "requires_redirect" to JsonPrimitive(false)
                                        )
                                    )
                                )

                            )
                        )
                    )
                )
            )
        } else {
            Result.failure(UnsupportedOperationException())
        }
    }

    override suspend fun login(username: String, password: String) {
        authorize("token", true)
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        _sessionState.update {
            when (it) {
                is SessionState.Connected.Direct -> {
                    SessionState.Connected.Direct(
                        it.connectionInfo,
                        it.connectionData.copy(
                            authProcessState = AuthProcessState.NotStarted,
                            user = User("-1", username, username, "user"),
                            wasAutoLogin = true
                        )
                    )
                }

                else -> throw IllegalStateException()
            }
        }
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    override val isReadyForCommands: StateFlow<Boolean>
        get() = TODO("Not yet implemented")

    private val _serverBaseUrl = MutableStateFlow<String?>(null)
    override val serverBaseUrl: StateFlow<String?> = _serverBaseUrl

    override fun forceWebRTCReconnect() {
        TODO("Not yet implemented")
    }

    override val events: Flow<Event<out Any>> = MutableSharedFlow()
    override val webrtcSendspinChannel: DataChannelWrapper?
        get() = TODO("Not yet implemented")

    override fun onAppForeground() {

    }

    override fun onAppBackground() {

    }

    override fun disconnectByUser() {
        TODO("Not yet implemented")
    }

    override fun connect(connection: ConnectionInfo) {
        settingsRepository.updateConnectionInfo(connection)
        val connectionData = ConnectionData(
            serverInfo = ServerInfo(
                serverVersion = "fake",
                schemaVersion = -1,
                baseUrl = "http://homeassistant.example"
            )
        )
        _sessionState.value = SessionState.Connected.Direct(connection, connectionData)
        _serverBaseUrl.value = connectionData.serverInfo?.baseUrl
    }

    override fun connectWebRTC(remoteId: RemoteId) {
        TODO("Not yet implemented")
    }

    override fun onExternalConsumerActive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackActive() {
        TODO("Not yet implemented")
    }

    override fun onExternalConsumerInactive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackInactive() {
        TODO("Not yet implemented")
    }

}