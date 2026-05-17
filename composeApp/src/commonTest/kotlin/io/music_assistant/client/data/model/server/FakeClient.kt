package io.music_assistant.client.data.model.server

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class FakeClient : ServiceClient {
    override val sessionState: StateFlow<SessionState>
        get() = TODO("Not yet implemented")

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
        TODO("Not yet implemented")
    }

    override fun onAppBackground() {
        TODO("Not yet implemented")
    }

    override val foregroundEvents: Flow<Unit>
        get() = TODO("Not yet implemented")

    override fun disconnectByUser() {
        TODO("Not yet implemented")
    }

    override fun connect(connection: ConnectionInfo) {
        TODO("Not yet implemented")
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
