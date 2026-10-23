package io.music_assistant.client.data

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelReceiveSource
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCDiagnostics
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

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
    fun anOldServerIsRejectedAndAnUpgradeRestoresTheEndpoint() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = direct(
            ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = 44), user = USER, token = "tok"),
        )
        runCurrent()
        assertIs<Endpoint.Unsupported>(endpoints.endpoint.value)

        client.sessionState.value = direct(
            ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = 45), user = USER, token = "tok"),
        )
        runCurrent()
        assertIs<Endpoint.WebSocket>(endpoints.endpoint.value)
    }

    @Test
    fun anOldWebRtcServerIsRejectedBeforeOpeningAChannel() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = SessionState.Connected.WebRTC(
            RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXY2"),
            ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = 44), user = USER, token = "tok"),
        )
        runCurrent()
        assertIs<Endpoint.Unsupported>(endpoints.endpoint.value)
    }

    @Test
    fun theSchemaPicksTheWire() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        listOf(
            null to WireVersion.LEGACY_9,
            SERVER_2_11_SCHEMA - 1 to WireVersion.LEGACY_9,
            SERVER_2_11_SCHEMA to WireVersion.SPEC_1_0,
        ).forEach { (schema, wire) ->
            client.sessionState.value = direct(
                ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = schema), user = USER, token = "tok"),
            )
            runCurrent()
            assertEquals(wire, assertIs<Endpoint.WebSocket>(endpoints.endpoint.value).wire, "schema $schema")
        }
    }

    @Test
    fun server2107PicksTheSpecWireAtThe210Schema() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = direct(
            ConnectionData(
                serverInfo = SERVER_INFO.copy(schemaVersion = 65, serverVersion = "2.10.7"),
                user = USER,
                token = "tok",
            ),
        )
        runCurrent()
        assertEquals(WireVersion.SPEC_1_0, assertIs<Endpoint.WebSocket>(endpoints.endpoint.value).wire)
    }

    @Test
    fun aWebRtcEndpointIsReusedOnlyOnTheSameWire() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        fun webRtc(schema: Int) = SessionState.Connected.WebRTC(
            RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXY2"),
            ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = schema), user = USER, token = "tok"),
        )
        client.sessionState.value = webRtc(SERVER_2_11_SCHEMA - 1)
        runCurrent()
        val legacy = assertIs<Endpoint.WebRtc>(endpoints.endpoint.value)
        assertEquals(WireVersion.LEGACY_9, legacy.wire)

        client.sessionState.value = webRtc(SERVER_2_11_SCHEMA)
        runCurrent()
        val spec = assertIs<Endpoint.WebRtc>(endpoints.endpoint.value)
        assertEquals(WireVersion.SPEC_1_0, spec.wire)
        assertNotSame(legacy, spec)

        client.sessionState.value = webRtc(SERVER_2_11_SCHEMA)
        runCurrent()
        assertSame(spec, endpoints.endpoint.value)
    }

    @Test
    fun aWebRtcReconnectKeepsTheEndpointUntilTheServerInfoArrives() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        val spec = SERVER_INFO.copy(schemaVersion = SERVER_2_11_SCHEMA)
        fun webRtc(data: ConnectionData) =
            SessionState.Connected.WebRTC(RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXY2"), data)

        client.sessionState.value = webRtc(ConnectionData(serverInfo = null, user = USER))
        runCurrent()
        assertNull(endpoints.endpoint.value, "no endpoint before server/hello")

        client.sessionState.value = webRtc(ConnectionData(serverInfo = spec, user = USER))
        runCurrent()
        val first = assertIs<Endpoint.WebRtc>(endpoints.endpoint.value)
        assertEquals(WireVersion.SPEC_1_0, first.wire)

        // A new peer connection clears the server info and the authorization.
        listOf(
            webRtc(ConnectionData(serverInfo = null, user = USER, needsServerReauth = true)),
            webRtc(ConnectionData(serverInfo = spec, user = USER, needsServerReauth = true)),
            webRtc(ConnectionData(serverInfo = spec, user = USER, token = "tok")),
        ).forEach { state ->
            client.sessionState.value = state
            runCurrent()
            assertSame(first, endpoints.endpoint.value, "kept through ${state.dataConnectionState}")
        }
    }

    private fun webRtcAt(schema: Int) = SessionState.Connected.WebRTC(
        RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXY2"),
        ConnectionData(serverInfo = SERVER_INFO.copy(schemaVersion = schema), user = USER, token = "tok"),
    )

    /** Opens the negotiated channel once, so the next attempt needs a fresh one. */
    private suspend fun spendNegotiatedChannel(endpoints: LocalPlayerEndpoints): Endpoint.WebRtc {
        val endpoint = assertIs<Endpoint.WebRtc>(endpoints.endpoint.value)
        client.webrtcSendspinChannel = openChannel()
        endpoint.openChannel()
        return endpoint
    }

    @Test
    fun aSpentChannelReopensOnTheLivePeerWithoutRenegotiating() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = webRtcAt(SERVER_2_11_SCHEMA)
        runCurrent()
        val endpoint = spendNegotiatedChannel(endpoints)
        client.reopened = openChannel()

        endpoint.openChannel()
        assertEquals(listOf("sendspin"), client.openedLabels)
        assertEquals(0, client.renegotiations)
    }

    @Test
    fun aServerWithoutLabelRoutingRenegotiates() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = webRtcAt(48)
        runCurrent()
        val endpoint = spendNegotiatedChannel(endpoints)
        client.reopened = openChannel()

        endpoint.openChannel()
        assertEquals(emptyList(), client.openedLabels, "an unrouted label would drop the session")
        assertEquals(1, client.renegotiations)
    }

    @Test
    fun aReopenThatFailsFallsBackToRenegotiation() = runTest {
        val endpoints = LocalPlayerEndpoints(client, settings, backgroundScope)
        client.sessionState.value = webRtcAt(SERVER_2_11_SCHEMA)
        runCurrent()
        val endpoint = spendNegotiatedChannel(endpoints)

        endpoint.openChannel()
        assertEquals(listOf("sendspin"), client.openedLabels)
        assertEquals(1, client.renegotiations)
    }

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

/** An open channel with nothing to deliver. */
private fun openChannel() = DataChannelWrapper(
    dataChannel = null,
    connectionEvents = null,
    receiveSource = object : DataChannelReceiveSource {
        override suspend fun receive(): DataChannelInbound = awaitCancellation()
    },
    initialState = DataChannelState.Open,
    label = "sendspin",
    diagnostics = WebRTCDiagnostics(),
)

/**
 * [sessionState] and the WebRTC channel seams are real; nothing else is used. A renegotiation
 * hands out a new negotiated channel, as a new peer connection does.
 */
private class SessionOnlyClient : ServiceClient {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
    override val isReadyForCommands: StateFlow<Boolean> get() = error("not used")
    override val externalConsumerActive: StateFlow<Boolean> get() = error("not used")
    override val events: Flow<Event<out Any>> = emptyFlow()
    override var webrtcSendspinChannel: DataChannelWrapper? = null

    /** What the live peer opens; null is a channel that did not open. */
    var reopened: DataChannelWrapper? = null
    val openedLabels = mutableListOf<String>()
    var renegotiations = 0

    override suspend fun openWebRTCDataChannel(label: String): DataChannelWrapper? {
        openedLabels += label
        return reopened
    }
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
    override fun forceWebRTCReconnect() {
        renegotiations++
        webrtcSendspinChannel = openChannel()
    }
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override fun disconnectByUser() = Unit
    override fun connect(connection: ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: RemoteId) = Unit
    override fun onExternalConsumerActive() = Unit
    override fun requestCommandRecovery() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
    override fun noServer() = Unit
}
