package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ServiceClient {
    val sessionState: StateFlow<SessionState>

    suspend fun sendRequest(request: Request): Result<Answer>
    suspend fun login(username: String, password: String)
    suspend fun authorize(token: String, isAutoLogin: Boolean = false)
    fun logout()
    val isReadyForCommands: StateFlow<Boolean>
    val serverBaseUrl: StateFlow<String?>
    fun forceWebRTCReconnect()
    val events: Flow<Event<out Any>>
    val webrtcSendspinChannel: DataChannelWrapper?

    fun onAppForeground()
    fun onAppBackground()
    fun disconnectByUser()
    fun connect(connection: ConnectionInfo)
    fun connectWebRTC(remoteId: RemoteId)
    fun onExternalConsumerActive()
    fun onPlaybackActive()
    fun onExternalConsumerInactive()
    fun onPlaybackInactive()
}
