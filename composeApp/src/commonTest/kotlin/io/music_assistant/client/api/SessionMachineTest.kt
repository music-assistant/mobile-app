package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionMachineTest {
    private val info = ConnectionInfo("host", 8095, isTls = false)
    private val savedInfo = ConnectionInfo("saved", 8095, isTls = true)
    private val remoteId = RemoteId("A".repeat(26))
    private val direct = SessionTarget.Direct(info)
    private val webrtc = SessionTarget.WebRTC(remoteId)
    private val data = ConnectionData(serverInfo = ServerInfo(serverId = "server-1"), token = "token")

    private fun env(
        isOnline: Boolean = true,
        isHeld: Boolean = false,
        transportState: TransportState? = null,
        savedConnectionInfo: ConnectionInfo? = null,
        lastHistory: ConnectionHistoryEntry? = null,
    ) = Env(isOnline, isHeld, transportState, savedConnectionInfo, lastHistory)

    private fun step(
        state: SessionState,
        event: SessionEvent,
        lifecycle: Lifecycle = Lifecycle(gen = GEN),
        env: Env = env(),
    ) = transition(lifecycle, state, event, env)

    private val Step.actions get() = effects.filterNot { it is Effect.Log }

    private fun startEffects(target: SessionTarget, resume: Boolean, gen: Int = GEN + 1) = listOf(
        Effect.ResetSilentReauth,
        Effect.DropTransport,
        Effect.Publish(SessionState.Connecting),
        Effect.FailAll("Transport replaced by a new connection"),
        Effect.OpenTransport(target, resume, gen),
    )

    // --- Resume from Backgrounded is synchronous: the caller's next request must see Connecting ---

    @Test
    fun foregroundFromBackgroundedEmitsThenResumesInTheSameStep() {
        val parked = Lifecycle(inBackground = true, backgroundedTarget = direct, gen = GEN)
        val result = step(SessionState.Disconnected.Backgrounded, SessionEvent.Foreground, parked)

        assertEquals(listOf(Effect.EmitForeground) + startEffects(direct, resume = true), result.actions)
        assertEquals(Lifecycle(gen = GEN + 1), result.lifecycle)
    }

    @Test
    fun aResumedDirectTargetFollowsTheSavedConnectionInfo() {
        val parked = Lifecycle(inBackground = true, backgroundedTarget = direct, gen = GEN)
        val result = step(
            SessionState.Disconnected.Backgrounded,
            SessionEvent.Foreground,
            parked,
            env(savedConnectionInfo = savedInfo),
        )

        assertTrue(Effect.OpenTransport(SessionTarget.Direct(savedInfo), true, GEN + 1) in result.actions)
    }

    @Test
    fun carActiveFromBackgroundedResumes() {
        val parked = Lifecycle(inBackground = true, backgroundedTarget = webrtc, gen = GEN)
        val result = step(SessionState.Disconnected.Backgrounded, SessionEvent.ExternalConsumerActive, parked)

        assertEquals(startEffects(webrtc, resume = true), result.actions)
        assertTrue(result.lifecycle.externalConsumer)
    }

    @Test
    fun carActiveFromBackgroundedWithoutTargetOnlyLogs() {
        val result = step(SessionState.Disconnected.Backgrounded, SessionEvent.ExternalConsumerActive)

        assertEquals(emptyList(), result.actions)
        assertTrue(result.lifecycle.externalConsumer)
    }

    @Test
    fun playbackActiveFromBackgroundedResumes() {
        val parked = Lifecycle(inBackground = true, backgroundedTarget = direct, gen = GEN)
        val result = step(SessionState.Disconnected.Backgrounded, SessionEvent.PlaybackActive, parked)

        assertEquals(startEffects(direct, resume = true), result.actions)
        assertTrue(result.lifecycle.playback)
    }

    @Test
    fun playbackAndCarFlagsOutsideBackgroundedOnlyChangeTheLifecycle() {
        val connected = SessionState.Connected.Direct(info, data)

        val playback = step(connected, SessionEvent.PlaybackActive)
        assertTrue(playback.actions.isEmpty() && playback.lifecycle.playback)
        val car = step(connected, SessionEvent.ExternalConsumerActive)
        assertTrue(car.actions.isEmpty() && car.lifecycle.externalConsumer)
        val held = Lifecycle(externalConsumer = true, playback = true, gen = GEN)
        assertEquals(
            Lifecycle(playback = true, gen = GEN),
            step(connected, SessionEvent.ExternalConsumerInactive, held).lifecycle,
        )
        assertEquals(
            Lifecycle(externalConsumer = true, gen = GEN),
            step(connected, SessionEvent.PlaybackInactive, held).lifecycle,
        )
    }

    @Test
    fun foregroundEmitsOnlyOnAReturnFromTheBackground() {
        val connected = SessionState.Connected.Direct(info, data)

        assertEquals(
            listOf(Effect.EmitForeground),
            step(connected, SessionEvent.Foreground, Lifecycle(inBackground = true, gen = GEN)).actions,
        )
        assertEquals(emptyList(), step(connected, SessionEvent.Foreground).actions)
        assertTrue(step(connected, SessionEvent.Background).lifecycle.inBackground)
    }

    // --- Send failure: a fresh Connecting, never Reconnecting ---

    @Test
    fun aSendFailureWhileConnectedStartsAFreshResumedConnect() {
        val result = step(
            SessionState.Connected.Direct(info, data),
            SessionEvent.SendFailed("players/all", Exception("closed")),
            env = env(savedConnectionInfo = savedInfo),
        )

        assertEquals(startEffects(SessionTarget.Direct(savedInfo), resume = true), result.actions)
        assertTrue(result.actions.none { it is Effect.Publish && it.state is SessionState.Reconnecting })
    }

    @Test
    fun aSendFailureDoesNotReconnectForLogoutOrAReconnectInProgress() {
        val connected = SessionState.Connected.Direct(info, data)
        val failure = Exception("closed")

        assertEquals(emptyList(), step(connected, SessionEvent.SendFailed(APICommands.AUTH_LOGOUT, failure)).actions)
        assertEquals(emptyList(), step(SessionState.Connecting, SessionEvent.SendFailed("x", failure)).actions)
        assertEquals(
            emptyList(),
            step(SessionState.Reconnecting.Direct(1, info, data), SessionEvent.SendFailed("x", failure)).actions,
        )
        assertEquals(
            emptyList(),
            step(
                connected,
                SessionEvent.SendFailed("x", failure),
                env = env(transportState = TransportState.Reconnecting(2)),
            ).actions,
        )
    }

    @Test
    fun aSendFailureWithoutTargetDefersAnError() {
        val result = step(SessionState.Disconnected.Initial, SessionEvent.SendFailed("x", Exception("closed")))

        val deferred = assertIs<Effect.Defer>(result.actions.single()).event
        assertIs<SessionState.Disconnected.Error>(assertIs<SessionEvent.Disconnect>(deferred).state)
    }

    // --- Forced WebRTC reconnect ---

    @Test
    fun aForcedWebRTCReconnectKeepsTheDataAtAttemptZero() {
        val result = step(SessionState.Connected.WebRTC(remoteId, data), SessionEvent.ForceWebRTCReconnect)

        assertEquals(
            listOf(
                Effect.Publish(SessionState.Reconnecting.WebRTC(0, remoteId, data)),
                Effect.ForceTransportReconnect,
            ),
            result.actions,
        )
        assertEquals(
            emptyList(),
            step(SessionState.Connected.Direct(info, data), SessionEvent.ForceWebRTCReconnect).actions,
        )
    }

    @Test
    fun aWebRTCReconnectReauthenticatesWithoutServerInfo() {
        val result = step(
            SessionState.Reconnecting.WebRTC(0, remoteId, data),
            SessionEvent.Transport(GEN, webrtc, TransportState.Connected),
        )

        val expected = data.copy(
            serverInfo = null,
            needsServerReauth = true,
            authProcessState = AuthProcessState.NotStarted,
        )
        assertEquals(
            listOf(Effect.CancelWatchdog, Effect.Publish(SessionState.Connected.WebRTC(remoteId, expected))),
            result.actions,
        )
    }

    @Test
    fun aDirectReconnectKeepsTheServerInfo() {
        val result = step(
            SessionState.Reconnecting.Direct(1, info, data),
            SessionEvent.Transport(GEN, direct, TransportState.Connected),
        )

        val published = assertIs<Effect.Publish>(result.actions[1]).state
        assertEquals(data.serverInfo, assertIs<SessionState.Connected.Direct>(published).connectionData.serverInfo)
    }

    // --- Watchdog ---

    @Test
    fun theWatchdogFailsAConnectEvenInTheBackground() {
        val background = Lifecycle(inBackground = true, gen = GEN)
        val result = step(SessionState.Connecting, SessionEvent.WatchdogFired(GEN), background)

        assertIs<SessionState.Disconnected.Error>(assertIs<Effect.Publish>(result.actions[0]).state)
        assertEquals(Effect.DisconnectTransport, result.actions[1])
        assertEquals(2, result.actions.size)

        val afterTeardown = step(
            SessionState.Disconnected.Error(null),
            SessionEvent.Transport(GEN, direct, TransportState.Disconnected),
            background,
        )
        assertEquals(listOf(Effect.FailAll("Transport disconnected")), afterTeardown.actions)
    }

    @Test
    fun aStaleOrLateWatchdogDoesNothing() {
        assertEquals(emptyList(), step(SessionState.Connecting, SessionEvent.WatchdogFired(GEN - 1)).effects)
        assertEquals(
            emptyList(),
            step(SessionState.Reconnecting.Direct(1, info, data), SessionEvent.WatchdogFired(GEN)).effects,
        )
    }

    // --- Request-driven recovery ---

    @Test
    fun aKickResumesOnlyFromBackgrounded() {
        val history = env(lastHistory = direct.historyEntry)

        assertEquals(
            startEffects(direct, resume = true),
            step(SessionState.Disconnected.Backgrounded, SessionEvent.Kick, env = history).actions,
        )
        assertEquals(
            startEffects(direct, resume = false),
            step(SessionState.Disconnected.Error(null), SessionEvent.Kick, env = history).actions,
        )
        assertEquals(
            startEffects(direct, resume = false),
            step(SessionState.Disconnected.Initial, SessionEvent.Kick, env = history).actions,
        )
    }

    @Test
    fun aKickFromHistoryCanStartWebRTC() {
        val history = env(lastHistory = ConnectionHistoryEntry(type = ConnectionType.WEBRTC, remoteId = remoteId.rawId))

        assertEquals(
            startEffects(webrtc, resume = false),
            step(SessionState.Disconnected.Initial, SessionEvent.Kick, env = history).actions,
        )
    }

    @Test
    fun aKickHonoursUserIntentAndInProgressStates() {
        val history = env(lastHistory = direct.historyEntry)

        listOf(
            SessionState.Disconnected.ByUser,
            SessionState.Disconnected.NoServerData,
            SessionState.Connecting,
            SessionState.Reconnecting.Direct(1, info, data),
            SessionState.Connected.Direct(info, data),
        ).forEach { assertEquals(emptyList(), step(it, SessionEvent.Kick, env = history).actions, "state $it") }
    }

    @Test
    fun aKickWithoutHistoryOnlyLogs() {
        assertEquals(emptyList(), step(SessionState.Disconnected.Error(null), SessionEvent.Kick).actions)
    }

    // --- Connect and start ---

    @Test
    fun aConnectStartsWithAFixedEffectOrderAndClearsTheBackgroundedTarget() {
        val parked = Lifecycle(backgroundedTarget = webrtc, gen = GEN)
        val result = step(SessionState.Disconnected.Initial, SessionEvent.Connect(direct), parked)

        assertEquals(startEffects(direct, resume = false), result.effects)
        assertNull(result.lifecycle.backgroundedTarget)
        assertEquals(GEN + 1, result.lifecycle.gen)
    }

    @Test
    fun aConnectIsIgnoredWhileConnectingOrConnected() {
        assertEquals(emptyList(), step(SessionState.Connecting, SessionEvent.Connect(direct)).effects)
        assertEquals(emptyList(), step(SessionState.Connected.Direct(info, data), SessionEvent.Connect(direct)).effects)
        assertEquals(
            startEffects(webrtc, resume = false),
            step(SessionState.Reconnecting.Direct(1, info, data), SessionEvent.Connect(webrtc)).actions,
        )
    }

    @Test
    fun aFreshConnectionIsRecorded() {
        listOf(SessionState.Connecting, SessionState.Disconnected.NoServerData).forEach { state ->
            val result = step(state, SessionEvent.Transport(GEN, direct, TransportState.Connected))
            assertEquals(
                listOf(
                    Effect.CancelWatchdog,
                    Effect.Publish(SessionState.Connected.Direct(info, ConnectionData())),
                    Effect.RecordConnection(direct),
                ),
                result.actions,
                "state $state",
            )
        }
    }

    // --- Transport loss ---

    @Test
    fun aReconnectInTheBackgroundParksWhenNothingHoldsTheSession() {
        val result = step(
            SessionState.Connected.Direct(info, data),
            SessionEvent.Transport(GEN, direct, TransportState.Reconnecting(1)),
            Lifecycle(inBackground = true, gen = GEN),
        )

        assertEquals(
            listOf(
                Effect.DisconnectTransport,
                Effect.Publish(SessionState.Disconnected.Backgrounded),
                Effect.FailAll("Transport reconnecting (attempt=1)"),
            ),
            result.actions,
        )
        assertEquals(direct, result.lifecycle.backgroundedTarget)
        assertEquals(GEN, result.lifecycle.gen)
    }

    @Test
    fun eachHoldKeepsTheSessionReconnectingInTheBackground() {
        val reconnecting = listOf(
            Effect.Publish(SessionState.Reconnecting.Direct(1, info, data, isOnline = false)),
            Effect.FailAll("Transport reconnecting (attempt=1)"),
        )
        val event = SessionEvent.Transport(GEN, direct, TransportState.Reconnecting(1))
        val connected = SessionState.Connected.Direct(info, data)
        val offline = env(isOnline = false)

        assertEquals(
            reconnecting,
            step(connected, event, Lifecycle(inBackground = true, externalConsumer = true, gen = GEN), offline).actions,
        )
        assertEquals(
            reconnecting,
            step(connected, event, Lifecycle(inBackground = true, playback = true, gen = GEN), offline).actions,
        )
        assertEquals(
            reconnecting,
            step(
                connected,
                event,
                Lifecycle(inBackground = true, gen = GEN),
                env(isOnline = false, isHeld = true),
            ).actions,
        )
        assertEquals(reconnecting, step(connected, event, env = offline).actions)
    }

    @Test
    fun aFailedTransportIsAnErrorInTheForegroundAndParksInTheBackground() {
        val failed = SessionEvent.Transport(GEN, direct, TransportState.Failed(Exception("refused")))
        val reconnecting = SessionState.Reconnecting.Direct(20, info, data)

        val foreground = step(reconnecting, failed).actions
        assertIs<SessionState.Disconnected.Error>(assertIs<Effect.Publish>(foreground[0]).state)
        assertEquals(Effect.FailAll("Transport failed: refused"), foreground[1])

        val background = step(reconnecting, failed, Lifecycle(inBackground = true, playback = true, gen = GEN))
        assertEquals(
            listOf(Effect.Publish(SessionState.Disconnected.Backgrounded), Effect.FailAll("Transport failed: refused")),
            background.actions,
        )
        assertEquals(direct, background.lifecycle.backgroundedTarget)
    }

    @Test
    fun aDisconnectedTransportIsUserIntentOnlyWhenTheSessionIsNotDisconnected() {
        val event = SessionEvent.Transport(GEN, direct, TransportState.Disconnected)

        assertEquals(
            listOf(Effect.Publish(SessionState.Disconnected.ByUser), Effect.FailAll("Transport disconnected")),
            step(SessionState.Connected.Direct(info, data), event).actions,
        )
        assertEquals(
            listOf(Effect.FailAll("Transport disconnected")),
            step(SessionState.Disconnected.Backgrounded, event).actions,
        )
    }

    @Test
    fun anEventOfAnOldTransportDoesNothing() {
        TRANSPORT_STATES.forEach { state ->
            assertEquals(
                emptyList(),
                step(SessionState.Connected.Direct(info, data), SessionEvent.Transport(GEN - 1, direct, state)).effects,
                "transport $state",
            )
        }
    }

    // --- Disconnect and no server ---

    @Test
    fun aDisconnectDropsTheTransportAndKeepsTheBackgroundedTarget() {
        val parked = Lifecycle(backgroundedTarget = direct, gen = GEN)
        val result = step(SessionState.Connecting, SessionEvent.Disconnect(SessionState.Disconnected.ByUser), parked)

        assertEquals(
            listOf(
                Effect.DropTransport,
                Effect.Publish(SessionState.Disconnected.ByUser),
                Effect.FailAll("Disconnected (Disconnected.ByUser)"),
            ),
            result.actions,
        )
        assertEquals(Lifecycle(backgroundedTarget = direct, gen = GEN + 1), result.lifecycle)
    }

    @Test
    fun noServerOnlyPublishes() {
        val result = step(SessionState.Disconnected.Initial, SessionEvent.NoServer)

        assertEquals(listOf(Effect.Publish(SessionState.Disconnected.NoServerData)), result.effects)
        assertEquals(Lifecycle(gen = GEN), result.lifecycle)
    }

    // --- Invariants ---

    @Test
    fun theStateIsPublishedBeforeRequestsFail() {
        val connected = SessionState.Connected.Direct(info, data)
        listOf(
            step(SessionState.Disconnected.Initial, SessionEvent.Connect(direct)),
            step(connected, SessionEvent.SendFailed("x", Exception("closed"))),
            step(connected, SessionEvent.Disconnect(SessionState.Disconnected.ByUser)),
            step(connected, SessionEvent.Transport(GEN, direct, TransportState.Reconnecting(1))),
            step(
                connected,
                SessionEvent.Transport(GEN, direct, TransportState.Reconnecting(1)),
                Lifecycle(inBackground = true, gen = GEN),
            ),
            step(connected, SessionEvent.Transport(GEN, direct, TransportState.Failed(Exception("x")))),
            step(connected, SessionEvent.Transport(GEN, direct, TransportState.Disconnected)),
        ).forEach { result ->
            val publish = result.effects.indexOfFirst { it is Effect.Publish }
            val failAll = result.effects.indexOfFirst { it is Effect.FailAll }
            assertTrue(publish in 0 until failAll, "effects ${result.effects}")
        }
    }

    @Test
    fun theDispatchLockIsReentrant() {
        val lock = SynchronizedObject()
        var nested = false
        synchronized(lock) { synchronized(lock) { nested = true } }
        assertTrue(nested)
    }

    private companion object {
        const val GEN = 7
        val TRANSPORT_STATES = listOf(
            TransportState.Connecting,
            TransportState.Connected,
            TransportState.Reconnecting(1),
            TransportState.Failed(Exception("x")),
            TransportState.Disconnected,
        )
    }
}
