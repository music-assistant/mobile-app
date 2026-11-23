package io.music_assistant.client.watchlink

import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WatchConfigPublisherTest {

    @Test
    fun directSignedInSessionSendsAddressAndToken() {
        val state = linkStateOf(direct(CONNECTION.copy(basePath = "ma/"), signedIn()))

        assertEquals(
            WatchLinkState.Configured(
                host = "192.168.1.10",
                port = 8095,
                isTls = false,
                basePath = "/ma",
                token = "token-1",
                serverId = "server-1",
            ),
            state,
        )
    }

    @Test
    fun webRtcSessionSendsRemoteOnly() {
        val state = linkStateOf(SessionState.Connected.WebRTC(REMOTE_ID, signedIn()))

        assertEquals(WatchLinkState.RemoteOnly, state)
    }

    @Test
    fun logoutAndRejectedTokenSendSignedOut() {
        assertEquals(WatchLinkState.SignedOut, linkStateOf(direct(CONNECTION, awaiting(AuthProcessState.LoggedOut))))
        assertEquals(
            WatchLinkState.SignedOut,
            linkStateOf(direct(CONNECTION, awaiting(AuthProcessState.Failed("rejected")))),
        )
    }

    @Test
    fun statesThatSayNothingAboutTheTokenSendNothing() {
        assertNull(linkStateOf(SessionState.Connecting))
        assertNull(linkStateOf(SessionState.Disconnected.Backgrounded))
        assertNull(linkStateOf(SessionState.Disconnected.ByUser))
        assertNull(linkStateOf(SessionState.Reconnecting.Direct(attempt = 1, connectionInfo = CONNECTION)))
        assertNull(linkStateOf(direct(CONNECTION, ConnectionData())))
        assertNull(linkStateOf(direct(CONNECTION, awaiting(AuthProcessState.NotStarted))))
        assertNull(linkStateOf(direct(CONNECTION, awaiting(AuthProcessState.InProgress))))
    }

    @Test
    fun reconnectToTheSameServerDoesNotSendAgain() = runTest {
        val session = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        val sent = mutableListOf<WatchLinkState>()
        WatchConfigPublisher(session, { sent += it }, backgroundScope.plusUnconfined(testScheduler))

        session.value = direct(CONNECTION, signedIn())
        session.value = SessionState.Reconnecting.Direct(attempt = 1, connectionInfo = CONNECTION)
        session.value = SessionState.Disconnected.Backgrounded
        session.value = direct(CONNECTION, signedIn())
        session.value = direct(CONNECTION, awaiting(AuthProcessState.LoggedOut))

        assertEquals(2, sent.size)
        assertEquals("token-1", (sent[0] as WatchLinkState.Configured).token)
        assertEquals(WatchLinkState.SignedOut, sent[1])
    }

    @Test
    fun aFailingLinkDoesNotStopLaterStates() = runTest {
        val session = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        val sent = mutableListOf<WatchLinkState>()
        val link = WatchLink { state ->
            sent += state
            if (state is WatchLinkState.Configured) error("no watch")
        }
        WatchConfigPublisher(session, link, backgroundScope.plusUnconfined(testScheduler))

        session.value = direct(CONNECTION, signedIn())
        session.value = direct(CONNECTION, awaiting(AuthProcessState.LoggedOut))

        assertEquals(WatchLinkState.SignedOut, sent.last())
    }

    private fun CoroutineScope.plusUnconfined(scheduler: TestCoroutineScheduler) =
        CoroutineScope(coroutineContext + UnconfinedTestDispatcher(scheduler))

    private fun direct(info: ConnectionInfo, data: ConnectionData) = SessionState.Connected.Direct(info, data)

    private fun signedIn() = ConnectionData(serverInfo = SERVER_INFO, user = USER, token = "token-1")

    private fun awaiting(auth: AuthProcessState) =
        ConnectionData(serverInfo = SERVER_INFO, authProcessState = auth)

    private companion object {
        val CONNECTION = ConnectionInfo(host = "192.168.1.10", port = 8095, isTls = false)
        val SERVER_INFO = ServerInfo(serverId = "server-1")
        val USER = User(username = "daveb")
        val REMOTE_ID = RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    }
}
