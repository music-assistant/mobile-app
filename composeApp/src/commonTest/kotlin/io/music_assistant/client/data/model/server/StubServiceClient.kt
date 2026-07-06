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

class StubServiceClient : ServiceClient {
    override val sessionState: StateFlow<SessionState>
        get() = TODO("Not yet implemented")

    override suspend fun sendRequest(request: Request): Result<Answer> {
        TODO("Not yet implemented")
    }

    override suspend fun login(username: String, password: String) {
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
    }

    override fun logout() {
    }

    override val isReadyForCommands: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val externalConsumerActive: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val serverBaseUrl: StateFlow<String?>
        get() = TODO("Not yet implemented")

    override fun resolveImageUrl(path: String, provider: String, isRemotelyAccessible: Boolean, proxyId: String?): String? = null

    override fun rebaseServerImageUrl(rawUrl: String): String? = null

    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy? = null

    override fun forceWebRTCReconnect() {
    }

    override val events: Flow<Event<out Any>>
        get() = TODO("Not yet implemented")
    override val webrtcSendspinChannel: DataChannelWrapper? = null

    override fun onAppForeground() {
    }

    override fun onAppBackground() {
    }

    override val foregroundEvents: Flow<Unit>
        get() = TODO("Not yet implemented")

    override fun disconnectByUser() {
    }

    override fun connect(connection: ConnectionInfo) {
    }

    override fun connectWebRTC(remoteId: RemoteId) {
    }

    override fun onExternalConsumerActive() {
    }

    override fun onPlaybackActive() {
    }

    override fun onExternalConsumerInactive() {
    }

    override fun onPlaybackInactive() {
    }

    override fun forceDisconnect(reason: Exception) {
    }
}
