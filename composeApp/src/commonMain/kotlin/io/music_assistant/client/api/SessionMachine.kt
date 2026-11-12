package io.music_assistant.client.api

import co.touchlab.kermit.Severity
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.model.RemoteId

/*
 * The connection lifecycle of the MA session as a pure, synchronous state machine.
 *
 * `KtorServiceClient.dispatch` runs [transition] and then its effects in order, under one lock, on the
 * caller's thread. So a state change is visible before the caller continues: a car host or a playback
 * start that resumes a backgrounded session sees `Connecting` at once, and its next request does not
 * start a second reconnect. Effects never suspend or block.
 *
 * The auth fields of [ConnectionData] (auth state, user, token, server info refresh) are not part of
 * this machine. The client updates them in place; the machine only reads them through the state.
 */

// Upper bound on a single connect attempt before it's declared stuck. Generous
// enough to cover a healthy cold-start handshake (incl. WebRTC signaling + ICE)
// so it never trips a merely-slow connect, but bounded so `Connecting` can't wedge.
internal const val CONNECT_TIMEOUT_MS = 20_000L

/** What the session connects to. Everything that differs between the two transports is here. */
internal sealed class SessionTarget {
    /** The value of `SettingsRepository.setLastConnectionMode`, also the log label. */
    abstract val mode: String
    abstract val historyEntry: ConnectionHistoryEntry

    /**
     * WebRTC clears `serverInfo` on a reconnect, because the gateway proxies the data channel onto a
     * fresh local WebSocket and races the two legs: if `client/auth` is sent before the gateway has
     * forwarded the new `server/hello` back through the (not-yet-`open`) channel, auth is silently
     * lost. Clearing `serverInfo` forces `AwaitingServerInfo`, holding `authorize` until the fresh
     * `server/hello` arrives. Direct WebSocket has no such ordering gate: the new `server/hello`
     * refreshes the cached value over the same FIFO channel that carries `client/auth` next.
     */
    abstract val clearsServerInfoOnReconnect: Boolean

    abstract fun connected(data: ConnectionData): SessionState.Connected
    abstract fun reconnecting(attempt: Int, data: ConnectionData, isOnline: Boolean): SessionState.Reconnecting

    data class Direct(val connectionInfo: ConnectionInfo) : SessionTarget() {
        override val mode = "direct"

        // Provisional: the server has not named itself yet, so this row has no id.
        // It keeps JIT reconnect able to recover a login that did not finish, and
        // AuthenticationManager absorbs it once the server identifies itself.
        override val historyEntry
            get() = ConnectionHistoryEntry(
                type = ConnectionType.DIRECT,
                host = connectionInfo.host,
                port = connectionInfo.port,
                isTls = connectionInfo.isTls,
                basePath = connectionInfo.basePath,
            )
        override val clearsServerInfoOnReconnect = false

        override fun connected(data: ConnectionData) = SessionState.Connected.Direct(connectionInfo, data)
        override fun reconnecting(attempt: Int, data: ConnectionData, isOnline: Boolean) =
            SessionState.Reconnecting.Direct(attempt, connectionInfo, data, isOnline)
    }

    data class WebRTC(val remoteId: RemoteId) : SessionTarget() {
        override val mode = "webrtc"

        // Provisional, like the Direct entry.
        override val historyEntry
            get() = ConnectionHistoryEntry(type = ConnectionType.WEBRTC, remoteId = remoteId.rawId)
        override val clearsServerInfoOnReconnect = true

        override fun connected(data: ConnectionData) = SessionState.Connected.WebRTC(remoteId, data)
        override fun reconnecting(attempt: Int, data: ConnectionData, isOnline: Boolean) =
            SessionState.Reconnecting.WebRTC(attempt, remoteId, data, isOnline)
    }
}

/** The lifecycle facts that are not in [SessionState]. */
internal data class Lifecycle(
    val inBackground: Boolean = false,
    /** Android Auto or CarPlay is bound. */
    val externalConsumer: Boolean = false,
    /** The local player plays, or a local pause waits for the server (two writers, the last one wins). */
    val playback: Boolean = false,
    /** The target of a session that was torn down in the background. The next resume reconnects it. */
    val backgroundedTarget: SessionTarget? = null,
    /** Transport generation. An event of a replaced or dropped transport carries an older one. */
    val gen: Int = 0,
)

/** The impure inputs, read once for each dispatch. */
internal data class Env(
    val isOnline: Boolean,
    /** A command asked for the session recently (`SessionHold`). */
    val isHeld: Boolean,
    val transportState: TransportState?,
    val savedConnectionInfo: ConnectionInfo?,
    val lastHistory: ConnectionHistoryEntry?,
)

internal sealed interface SessionEvent {
    data class Connect(val target: SessionTarget) : SessionEvent
    data object Foreground : SessionEvent
    data object Background : SessionEvent
    data object ExternalConsumerActive : SessionEvent
    data object ExternalConsumerInactive : SessionEvent
    data object PlaybackActive : SessionEvent
    data object PlaybackInactive : SessionEvent

    /** Request-driven recovery for a session that is not connected. */
    data object Kick : SessionEvent
    data object ForceWebRTCReconnect : SessionEvent
    data class Disconnect(val state: SessionState.Disconnected) : SessionEvent
    data object NoServer : SessionEvent
    data class SendFailed(val command: String, val error: Exception) : SessionEvent
    data class Transport(val gen: Int, val target: SessionTarget, val state: TransportState) : SessionEvent
    data class WatchdogFired(val gen: Int) : SessionEvent
}

internal sealed interface Effect {
    data class Publish(val state: SessionState) : Effect
    data class FailAll(val cause: String) : Effect
    data object ResetSilentReauth : Effect

    /** Cancel the observer, disconnect and close the transport, forget it. */
    data object DropTransport : Effect

    /** Disconnect the transport, but keep it and its observer. */
    data object DisconnectTransport : Effect

    /** Arm the watchdog, create and observe the transport for [gen], connect it. */
    data class OpenTransport(val target: SessionTarget, val resume: Boolean, val gen: Int) : Effect
    data object CancelWatchdog : Effect

    /** Save the connection mode and the provisional history entry of a fresh connection. */
    data class RecordConnection(val target: SessionTarget) : Effect
    data object EmitForeground : Effect
    data object ForceTransportReconnect : Effect

    /** Dispatch [event] later, from the client scope. */
    data class Defer(val event: SessionEvent) : Effect
    data class Log(val message: String, val severity: Severity = Severity.Info) : Effect
}

internal data class Step(val lifecycle: Lifecycle, val effects: List<Effect>)

internal fun transition(lifecycle: Lifecycle, state: SessionState, event: SessionEvent, env: Env): Step =
    Transition(lifecycle, state, env).apply { on(event) }.step()

private class Transition(
    private var lifecycle: Lifecycle,
    private val state: SessionState,
    private val env: Env,
) {
    private val effects = mutableListOf<Effect>()

    fun step() = Step(lifecycle, effects.toList())

    fun on(event: SessionEvent) {
        when (event) {
            is SessionEvent.Connect -> {
                val idle = state !is SessionState.Connecting && state !is SessionState.Connected
                if (idle) start(event.target, resume = false)
            }

            SessionEvent.Background -> {
                lifecycle = lifecycle.copy(inBackground = true)
                log("App backgrounded (state=${stateLabel(state)})")
            }

            SessionEvent.Foreground -> {
                val wasInBackground = lifecycle.inBackground
                lifecycle = lifecycle.copy(inBackground = false)
                log("App foregrounded (state=${stateLabel(state)})")
                if (wasInBackground) effects += Effect.EmitForeground
                if (lifecycle.backgroundedTarget != null) resume("was Backgrounded")
            }

            SessionEvent.ExternalConsumerActive -> {
                lifecycle = lifecycle.copy(externalConsumer = true)
                log("External consumer active (state=${stateLabel(state)})")
                if (state is SessionState.Disconnected.Backgrounded &&
                    !resume("external consumer active (was Backgrounded)")
                ) {
                    log("External consumer active: state=Backgrounded but no savedInfo, no reconnect")
                }
            }

            SessionEvent.ExternalConsumerInactive -> {
                lifecycle = lifecycle.copy(externalConsumer = false)
                log("External consumer inactive (state=${stateLabel(state)})")
            }

            SessionEvent.PlaybackActive -> {
                lifecycle = lifecycle.copy(playback = true)
                log("Playback active (state=${stateLabel(state)})")
                if (state is SessionState.Disconnected.Backgrounded) resume("playback active (was Backgrounded)")
            }

            SessionEvent.PlaybackInactive -> {
                lifecycle = lifecycle.copy(playback = false)
                log("Playback inactive (state=${stateLabel(state)})")
            }

            SessionEvent.Kick -> kick()

            SessionEvent.ForceWebRTCReconnect -> (state as? SessionState.Connected.WebRTC)?.let {
                log("Forcing WebRTC reconnect for fresh sendspin channel")
                // `connectionData` is carried verbatim so `Reconnecting.WebRTC` keeps the current
                // `user` for UI display during the reconnect window. The reauth-sensitive fields
                // get rebuilt when the new peer connection lands and emits Connected.
                effects += Effect.Publish(SessionState.Reconnecting.WebRTC(0, it.remoteId, it.connectionData))
                effects += Effect.ForceTransportReconnect
            }

            is SessionEvent.Disconnect -> drop(event.state, "Disconnected (${stateLabel(event.state)})")

            SessionEvent.NoServer -> effects += Effect.Publish(SessionState.Disconnected.NoServerData)

            is SessionEvent.SendFailed -> sendFailed(event)

            is SessionEvent.Transport -> if (event.gen == lifecycle.gen) onTransport(event.target, event.state)

            is SessionEvent.WatchdogFired ->
                if (event.gen == lifecycle.gen && state is SessionState.Connecting) {
                    log(
                        "Connect watchdog: stuck in Connecting after ${CONNECT_TIMEOUT_MS}ms — failing out",
                        Severity.Warn,
                    )
                    // Error first, then disconnect: the transport's `Disconnected` that follows then
                    // finds a `Disconnected` state and is not read as user intent (`ByUser`), which
                    // `kickRecovery` would refuse to recover from.
                    effects += Effect.Publish(
                        SessionState.Disconnected.Error(Exception("Connect timed out after ${CONNECT_TIMEOUT_MS}ms")),
                    )
                    effects += Effect.DisconnectTransport
                }
        }
    }

    private fun kick() {
        when (state) {
            SessionState.Disconnected.Initial,
            is SessionState.Disconnected.Error,
                -> history("JIT: state=${stateLabel(state)}", resume = false)

            SessionState.Disconnected.Backgrounded -> history("JIT: state=${stateLabel(state)}", resume = true)

            SessionState.Disconnected.ByUser,
            SessionState.Disconnected.NoServerData,
                -> log("JIT: honoring user-intent state=${stateLabel(state)}")

            // In progress, or the client's auth recovery.
            SessionState.Connecting,
            is SessionState.Reconnecting,
            is SessionState.Connected,
                -> Unit
        }
    }

    /**
     * A send failure is definitive liveness evidence: the Connected state can lag behind a closed
     * WebRTC data channel. Start a fresh connect, so callers that queue on failure are replayed when
     * the transport returns. Logout is user-intent teardown; never resurrect it.
     */
    private fun sendFailed(event: SessionEvent.SendFailed) {
        val canStartReconnect = event.command != APICommands.AUTH_LOGOUT &&
            state !is SessionState.Connecting &&
            state !is SessionState.Reconnecting &&
            env.transportState !is TransportState.Reconnecting
        if (canStartReconnect && !resume("send failed: ${event.error.message}")) {
            effects += Effect.Defer(
                SessionEvent.Disconnect(
                    SessionState.Disconnected.Error(Exception("Error sending command: ${event.error.message}")),
                ),
            )
        }
    }

    private fun onTransport(target: SessionTarget, transportState: TransportState) {
        when (transportState) {
            TransportState.Connected -> {
                effects += Effect.CancelWatchdog
                val preserved = (state as? HasConnectionData)?.connectionData ?: ConnectionData()
                val wasReconnecting = state is SessionState.Reconnecting
                // Both transports use per-connection auth state on the server, so every reconnect
                // needs a fresh `client/auth`. `needsServerReauth` lets AuthenticationManager drive it
                // from the resulting `AwaitingAuth(NotStarted)` state, and keeps gating RPCs even if
                // `authorize` later fails. `serverInfo` handling differs by transport, see
                // `SessionTarget.clearsServerInfoOnReconnect`. `authProcessState` resets to
                // `NotStarted` so a stale `InProgress` does not park AuthenticationManager. `user`
                // is preserved so the UI keeps showing the user as logged in.
                val data = if (wasReconnecting) {
                    preserved.copy(
                        serverInfo = if (target.clearsServerInfoOnReconnect) null else preserved.serverInfo,
                        needsServerReauth = true,
                        authProcessState = AuthProcessState.NotStarted,
                    )
                } else {
                    preserved
                }
                effects += Effect.Publish(target.connected(data))
                log("Transport→Connected (wasReconnecting=$wasReconnecting, dcs=${dcsLabel(data.dataConnectionState)})")
                if (wasReconnecting) {
                    log("${target.mode} reconnection successful — awaiting AuthenticationManager re-auth")
                } else {
                    effects += Effect.RecordConnection(target)
                }
            }

            is TransportState.Reconnecting -> {
                val canTearDown = lifecycle.inBackground && !lifecycle.externalConsumer &&
                    !lifecycle.playback && !env.isHeld
                if (canTearDown) {
                    lifecycle = lifecycle.copy(backgroundedTarget = target)
                    effects += Effect.DisconnectTransport
                    effects += Effect.Publish(SessionState.Disconnected.Backgrounded)
                    log("Transport→Reconnecting while backgrounded → Disconnected.Backgrounded")
                } else {
                    val preserved = (state as? HasConnectionData)?.connectionData ?: ConnectionData()
                    effects += Effect.Publish(target.reconnecting(transportState.attempt, preserved, env.isOnline))
                    log("Transport→Reconnecting (attempt=${transportState.attempt})")
                }
                // After the state update: a command re-queued by this failure must
                // see "not ready", not replay onto the dying socket.
                effects += Effect.FailAll("Transport reconnecting (attempt=${transportState.attempt})")
            }

            is TransportState.Failed -> {
                // In the background nobody sees an error, and Backgrounded reconnects on
                // the next foreground without leaving the screen. The error state, and
                // the Settings screen it leads to, is for a failure the user can watch.
                if (lifecycle.inBackground) {
                    lifecycle = lifecycle.copy(backgroundedTarget = target)
                    effects += Effect.Publish(SessionState.Disconnected.Backgrounded)
                    log(
                        "Transport→Failed while backgrounded → Disconnected.Backgrounded: ${transportState.error.message}",
                    )
                } else {
                    effects += Effect.Publish(SessionState.Disconnected.Error(transportState.error))
                    log("Transport→Failed → Disconnected.Error: ${transportState.error.message}")
                }
                effects += Effect.FailAll("Transport failed: ${transportState.error.message}")
            }

            TransportState.Disconnected -> {
                if (state !is SessionState.Disconnected) {
                    effects += Effect.Publish(SessionState.Disconnected.ByUser)
                    log("Transport→Disconnected → Disconnected.ByUser")
                }
                effects += Effect.FailAll("Transport disconnected")
            }

            TransportState.Connecting -> Unit
        }
    }

    /**
     * A new connection episode. The silent re-auth budget is per server session, and the backgrounded
     * target is consumed: a stale copy would make the next foreground tear down this live transport.
     */
    private fun start(target: SessionTarget, resume: Boolean) {
        val gen = lifecycle.gen + 1
        lifecycle = lifecycle.copy(backgroundedTarget = null, gen = gen)
        effects += Effect.ResetSilentReauth
        effects += Effect.DropTransport
        effects += Effect.Publish(SessionState.Connecting)
        effects += Effect.FailAll("Transport replaced by a new connection")
        effects += Effect.OpenTransport(target, resume, gen)
    }

    /**
     * Reconnects the backgrounded target or, failing that, the current connection. A resumed connect
     * retries a failed first attempt as `Reconnecting`, which keeps the current screen, instead of
     * `Disconnected.Error`, which sends the user to Settings. Returns false when there is no target.
     */
    private fun resume(reason: String): Boolean {
        val target = lifecycle.backgroundedTarget
            ?: when (state) {
                is SessionState.Connected.Direct -> SessionTarget.Direct(state.connectionInfo)
                is SessionState.Connected.WebRTC -> SessionTarget.WebRTC(state.remoteId)
                else -> null
            }
            ?: return false
        log("Force reconnect: $reason")
        // A direct session follows the saved connection info.
        val resumed = when (target) {
            is SessionTarget.Direct -> SessionTarget.Direct(env.savedConnectionInfo ?: target.connectionInfo)
            is SessionTarget.WebRTC -> target
        }
        start(resumed, resume = true)
        return true
    }

    private fun history(reason: String, resume: Boolean) {
        val entry = env.lastHistory ?: run {
            log("JIT: no history entry — cannot reconnect ($reason)")
            return
        }
        log("JIT reconnect via ${entry.type} ($reason)")
        val target = when (entry.type) {
            ConnectionType.DIRECT -> entry.connectionInfo?.let { SessionTarget.Direct(it) }
            ConnectionType.WEBRTC -> entry.remoteId?.let { SessionTarget.WebRTC(RemoteId(it)) }
        } ?: return
        start(target, resume)
    }

    private fun drop(newState: SessionState.Disconnected, cause: String) {
        lifecycle = lifecycle.copy(gen = lifecycle.gen + 1)
        effects += Effect.DropTransport
        effects += Effect.Publish(newState)
        effects += Effect.FailAll(cause)
    }

    private fun log(message: String, severity: Severity = Severity.Info) {
        effects += Effect.Log(message, severity)
    }
}

/** Compact one-token label for a [SessionState], stable for log greps. */
internal fun stateLabel(state: SessionState): String = when (state) {
    is SessionState.Connected.Direct -> "Connected.Direct(${dcsLabel(state.dataConnectionState)})"
    is SessionState.Connected.WebRTC -> "Connected.WebRTC(${dcsLabel(state.dataConnectionState)})"
    is SessionState.Reconnecting.Direct -> "Reconnecting.Direct(attempt=${state.attempt})"
    is SessionState.Reconnecting.WebRTC -> "Reconnecting.WebRTC(attempt=${state.attempt})"
    SessionState.Disconnected.Initial -> "Disconnected.Initial"
    SessionState.Disconnected.NoServerData -> "Disconnected.NoServerData"
    SessionState.Disconnected.Backgrounded -> "Disconnected.Backgrounded"
    SessionState.Disconnected.ByUser -> "Disconnected.ByUser"
    is SessionState.Disconnected.Error -> "Disconnected.Error(${state.reason?.message ?: state.reason?.toString()})"
    SessionState.Connecting -> "Connecting"
}

/** Compact one-token label for a [DataConnectionState]. */
internal fun dcsLabel(dcs: DataConnectionState): String = when (dcs) {
    DataConnectionState.AwaitingServerInfo -> "AwaitingServerInfo"
    is DataConnectionState.AwaitingAuth -> "AwaitingAuth"
    is DataConnectionState.Authenticated -> "Authenticated"
}
