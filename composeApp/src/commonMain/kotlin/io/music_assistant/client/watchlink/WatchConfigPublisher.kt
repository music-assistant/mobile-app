package io.music_assistant.client.watchlink

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sends the server and the token of the phone session to the watch app.
 *
 * It sends only when the session says something new: a signed-in session, a sign-out, or a
 * rejected token. A reconnect, a background teardown or a connection error says nothing about
 * the token, so the watch keeps the last state. The platform link keeps the last state too, so
 * a watch that was off gets it when it comes back.
 */
class WatchConfigPublisher(
    sessionState: Flow<SessionState>,
    private val link: WatchLink,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            sessionState
                .map(::linkStateOf)
                .filterNotNull()
                .distinctUntilChanged()
                .collect { state ->
                    log.i { "Publishing ${state::class.simpleName} to the watch" }
                    runCatching { link.publish(state) }
                        .onFailure { log.w(it) { "Watch link failed" } }
                }
        }
    }

    private companion object {
        val log = Logger.withTag("WatchLink")
    }
}

/** The state to send for one session state, or null when it says nothing new. */
internal fun linkStateOf(state: SessionState): WatchLinkState? {
    val connected = state as? SessionState.Connected ?: return null
    return when (val data = connected.dataConnectionState) {
        is DataConnectionState.Authenticated -> when (connected) {
            is SessionState.Connected.Direct -> connected.connectionInfo.toLinkState(
                token = data.token,
                serverId = data.serverInfo.serverId,
            )

            is SessionState.Connected.WebRTC -> WatchLinkState.RemoteOnly
        }

        is DataConnectionState.AwaitingAuth -> when (data.authProcessState) {
            AuthProcessState.LoggedOut, is AuthProcessState.Failed -> WatchLinkState.SignedOut
            AuthProcessState.NotStarted, AuthProcessState.InProgress -> null
        }

        DataConnectionState.AwaitingServerInfo -> null
    }
}

private fun ConnectionInfo.toLinkState(token: String, serverId: String) = WatchLinkState.Configured(
    host = host,
    port = port,
    isTls = isTls,
    basePath = ConnectionInfo.normalizeBasePath(basePath),
    token = token,
    serverId = serverId,
)
