package io.music_assistant.client.support

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeServiceClient(private val settingsRepository: SettingsRepository) : ServiceClient {

    val username = "user"
    val password = "password"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    override suspend fun sendRequest(request: Request): Result<Answer> {
        TODO("Not yet implemented")
    }

    override suspend fun login(username: String, password: String) {
        TODO("Not yet implemented")
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        TODO("Not yet implemented")
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    override val isReadyForCommands: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val serverBaseUrl: StateFlow<String?>
        get() = TODO("Not yet implemented")

    override fun forceWebRTCReconnect() {
        TODO("Not yet implemented")
    }

    override val events: Flow<Event<out Any>>
        get() = TODO("Not yet implemented")
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
        _sessionState.value = SessionState.Connected.Direct(connection)
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