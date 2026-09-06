package io.music_assistant.client.data

import com.russhwolf.settings.MapSettings
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
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import io.music_assistant.sendspin.api.Endpoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The endpoint flow owns the local player's lifetime now that MainDataSource
 * no longer starts or stops it: a terminal auth state must clear the endpoint,
 * a transient one must keep it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlayerEndpointsTest {
    private val client = SessionOnlyClient()
    private val settings = SettingsRepository(MapSettings(), MapSettings())

    private fun direct(data: ConnectionData) = SessionState.Connected.Direct(CONNECTION, data)

    private val authenticated = direct(ConnectionData(serverInfo = SERVER_INFO, user = USER, token = "tok"))

    private fun awaitingAuth(process: AuthProcessState) =
        direct(ConnectionData(serverInfo = SERVER_INFO, user = null, authProcessState = process))

    @Test
    fun aProvenSessionYieldsTheProxiedWebSocketEndpoint() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = authenticated
        runCurrent()
        val endpoint = assertIs<Endpoint.WebSocket>(endpoints.endpoint.value)
        assertEquals("ws://ma.local:8095/sendspin", endpoint.url)
        assertEquals("tok", endpoint.authToken)
    }

    @Test
    fun aFailedReauthorizationClearsTheEndpoint() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = authenticated
        runCurrent()
        client.sessionState.value = awaitingAuth(AuthProcessState.Failed("expired"))
        runCurrent()
        assertNull(endpoints.endpoint.value)
    }

    @Test
    fun aLogoutWhileConnectedClearsTheEndpoint() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = authenticated
        runCurrent()
        client.sessionState.value = awaitingAuth(AuthProcessState.LoggedOut)
        runCurrent()
        assertNull(endpoints.endpoint.value)
    }

    @Test
    fun transientStatesKeepTheEndpoint() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = authenticated
        runCurrent()
        val kept = endpoints.endpoint.value
        listOf(
            awaitingAuth(AuthProcessState.InProgress),
            awaitingAuth(AuthProcessState.NotStarted),
            SessionState.Reconnecting.Direct(attempt = 1, connectionInfo = CONNECTION),
            SessionState.Disconnected.Error(null),
            SessionState.Disconnected.Backgrounded,
            SessionState.Connecting,
        ).forEach { state ->
            client.sessionState.value = state
            runCurrent()
            assertEquals(kept, endpoints.endpoint.value, "kept through $state")
        }
    }

    private companion object {
        val CONNECTION = ConnectionInfo(host = "ma.local", port = 8095, isTls = false)
        val SERVER_INFO = ServerInfo(serverId = "server-1")
        val USER = User(username = "daveb")
    }
}

/** Only [sessionState] is real; the endpoint reducer touches nothing else. */
private class SessionOnlyClient : ServiceClient {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
    override val isReadyForCommands: StateFlow<Boolean> get() = error("not used")
    override val externalConsumerActive: StateFlow<Boolean> get() = error("not used")
    override val events: Flow<Event<out Any>> = emptyFlow()
    override val webrtcSendspinChannel: DataChannelWrapper? = null
    override val webRTCHttpProxy: WebRTCHttpProxy? = null
    override val foregroundEvents: Flow<Unit> = emptyFlow()

    override suspend fun sendRequest(request: Request): Result<Answer> = error("not used")
    override suspend fun login(username: String, password: String) = Unit
    override suspend fun authorize(token: String, isAutoLogin: Boolean) = Unit
    override fun logout() = Unit
    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null
    override fun rebaseServerImageUrl(rawUrl: String): String? = null
    override fun forceWebRTCReconnect() = Unit
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override fun disconnectByUser() = Unit
    override fun connect(connection: ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: RemoteId) = Unit
    override fun onExternalConsumerActive() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
    override fun noServer() = Unit
}
