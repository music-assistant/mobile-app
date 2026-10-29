package io.music_assistant.client.data

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.utils.SessionState
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinPlayer
import io.music_assistant.sendspin.api.StopCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The adapter against a fake Sendspin player: what a local pause does to the hold,
 * to the server, and to the session keep-alive ([LocalPlayerAdapter.wantsSession]).
 * The adapter runs on its own IO scope, so the tests wait on state in real time.
 */
class LocalPlayerAdapterTest {
    private val client = RecordingClient()
    private val settings = SettingsRepository(MapSettings(), MapSettings()).apply { setSendspinEnabled(true) }
    private val player = FakePlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracker = PlayerPositionTracker()
    private val adapter = run {
        val preferences = UserPreferences(client)
        LocalPlayerAdapter(
            settings = settings,
            apiClient = client,
            playerRequestFactory = PlayerRequestFactory(tracker, preferences),
            positionTracker = tracker,
            userPreferences = preferences,
            errorBus = ErrorMessageBus(),
            endpoints = LocalPlayerEndpoints(client, settings, scope),
            playerFactory = { _, _, _ -> player },
        )
    }

    @AfterTest
    fun tearDown() {
        adapter.cancel()
        scope.cancel()
    }

    @Test
    fun aCodecChangeWhilePlayingRestartsTheStreamOnceTheNewConnectionIsActive() = runBlocking<Unit> {
        playing()
        settings.setSendspinPlayerSettings(settings.sendspinPlayerSettings.value.copy(codecPreference = AudioCodec.PCM))
        delay(SETTLE_MS)
        assertTrue(client.sent.tryReceive().isFailure, "nothing before the new connection")

        // The adapter, and the restart that waits for the new connection.
        player.emit(PlayerEvent.ServerRefreshNeeded, subscribers = 2)

        assertEquals(PAUSE, client.nextSent().command)
        assertEquals(PLAY, client.nextSent().command)
    }

    @Test
    fun aCodecChangeWhileNotPlayingRestartsNothing() = runBlocking<Unit> {
        adapter.onInitialPlayersReceived(hasLocalPlayer = false)
        await { adapter.localPlayerData.value?.player }
        settings.setSendspinPlayerSettings(settings.sendspinPlayerSettings.value.copy(codecPreference = AudioCodec.PCM))
        delay(SETTLE_MS)

        player.emit(PlayerEvent.ServerRefreshNeeded)
        delay(SETTLE_MS)

        assertTrue(client.sent.tryReceive().isFailure)
    }

    @Test
    fun starvationPausesLikeTheUserAndKeepsTheSessionWanted() = runBlocking<Unit> {
        playing()
        player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))

        assertEquals(PAUSE, client.nextSent().command)
        await { player.held.value.takeIf { it } }
        assertFalse(adapter.localPlayerData.value?.player?.isPlaying == true)
        assertTrue(adapter.wantsSession.value)
    }

    @Test
    fun theServerConfirmingThePauseReleasesTheHoldAndTheSession() = runBlocking<Unit> {
        val local = playing()
        player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))
        client.nextSent()

        adapter.onServerPlayerUpdate(local.copy(isPlaying = false))

        await { player.held.value.takeUnless { it } }
        await { adapter.wantsSession.value.takeUnless { it } }
        assertFalse(adapter.wantsSession.value)
    }

    @Test
    fun starvationWhileAlreadyPausedChangesNothing() = runBlocking<Unit> {
        adapter.onInitialPlayersReceived(hasLocalPlayer = false)
        await { adapter.localPlayerData.value }

        player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))
        delay(SETTLE_MS)

        assertTrue(client.sent.tryReceive().isFailure)
        assertFalse(player.held.value)
        assertFalse(adapter.wantsSession.value)
    }

    @Test
    fun aStreamStartedWhileHeldIsAnsweredWithAnotherPause() = runBlocking<Unit> {
        val local = playing()
        player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))
        client.nextSent()

        player.emit(PlayerEvent.StreamStartedWhileHeld(local.id))

        assertEquals(PAUSE, client.nextSent().command)
        assertTrue(player.held.value)
    }

    @Test
    fun aFocusLossWhilePlayingResumesOnFocusRegained() = runBlocking<Unit> {
        playing()
        player.emit(PlayerEvent.PlaybackStopped(StopCause.FocusLost))
        assertEquals(PAUSE, client.nextSent().command)

        player.emit(PlayerEvent.FocusRegained)

        // The play lifts the hold, so a resync pause goes first.
        assertEquals(PAUSE, client.nextSent().command)
        assertEquals(PLAY, client.nextSent().command)
    }

    @Test
    fun anOfflinePauseAsksForRecoveryAndKeepsTheSessionWantedUntilItLands() = runBlocking<Unit> {
        val local = playing()
        client.isReadyForCommands.value = false

        adapter.handleLocalCommand(requireNotNull(adapter.localPlayerData.value), PlayerAction.Pause)

        await { client.recoveries.value.takeIf { it > 0 } }
        assertTrue(client.sent.tryReceive().isFailure)
        assertTrue(adapter.wantsSession.value)

        client.isReadyForCommands.value = true
        assertEquals(PAUSE, client.nextSent().command)
        adapter.onServerPlayerUpdate(local.copy(isPlaying = false))
        await { adapter.wantsSession.value.takeUnless { it } }
        assertFalse(adapter.wantsSession.value)
    }

    @Test
    fun aStopHoldsAtOnceAndTheServerIdleReleasesIt() = runBlocking<Unit> {
        val local = playing()

        adapter.handleLocalCommand(requireNotNull(adapter.localPlayerData.value), PlayerAction.Stop)

        assertEquals(STOP, client.nextSent().command)
        assertTrue(player.held.value)
        assertFalse(adapter.localPlayerData.value?.player?.isPlaying == true)

        adapter.onServerPlayerUpdate(local.copy(isPlaying = false))
        await { player.held.value.takeUnless { it } }
        assertFalse(player.held.value)
    }

    @Test
    fun clearingTheStateDropsTheSessionWantButKeepsTheHold() = runBlocking<Unit> {
        playing()
        player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))
        client.nextSent()

        adapter.clearState()

        await { adapter.wantsSession.value.takeUnless { it } }
        assertTrue(player.held.value)
    }

    @Test
    fun aNextOnTheLastItemKeepsThePosition() = runBlocking<Unit> {
        playingAt(currentIndex = 0, itemCount = 1)

        adapter.handleLocalCommand(requireNotNull(adapter.localPlayerData.value), PlayerAction.Next)

        assertEquals(NEXT, client.nextSent().command)
        assertFalse(tracker.isFrozenUntilConfirmed(QUEUE_ID))
        assertEquals(EPISODE_POSITION_SEC, tracker.effectiveSec(QUEUE_ID))
    }

    @Test
    fun aNextWithAnItemAfterItFreezesAtTheStart() = runBlocking<Unit> {
        playingAt(currentIndex = 0, itemCount = 2)

        adapter.handleLocalCommand(requireNotNull(adapter.localPlayerData.value), PlayerAction.Next)

        assertEquals(NEXT, client.nextSent().command)
        assertTrue(tracker.isFrozenUntilConfirmed(QUEUE_ID))
        assertEquals(0.0, tracker.effectiveSec(QUEUE_ID))
    }

    /** The local player plays item [currentIndex] of [itemCount], paused in the tracker at a known position. */
    private suspend fun playingAt(currentIndex: Int, itemCount: Int) {
        playing()
        adapter.onServerQueueUpdate(
            QueueInfo(
                id = QUEUE_ID,
                available = true,
                currentIndex = currentIndex,
                shuffleEnabled = false,
                repeatMode = RepeatMode.OFF,
                autoPlayEnabled = false,
                elapsedTime = EPISODE_POSITION_SEC,
                elapsedTimeLastUpdated = null,
                currentItem = null,
                radioSource = emptyList(),
                itemCount = itemCount,
            ),
        )
        tracker.setAnchor(QUEUE_ID, elapsedSec = EPISODE_POSITION_SEC, isPlaying = false)
    }

    /** Injects the synthetic local player and lets the server report it as playing. */
    private suspend fun playing(): Player {
        adapter.onInitialPlayersReceived(hasLocalPlayer = false)
        val synthetic = await { adapter.localPlayerData.value?.player }
        adapter.onServerPlayerUpdate(synthetic.copy(isPlaying = true))
        await { adapter.wantsSession.value.takeIf { it } }
        return synthetic
    }

    private suspend fun <T : Any> await(read: () -> T?): T = withTimeout(TIMEOUT_MS) {
        var value = read()
        while (value == null) {
            delay(POLL_MS)
            value = read()
        }
        value
    }

    private class FakePlayer : SendspinPlayer {
        override val state = MutableStateFlow<PlayerState>(PlayerState.Disabled)
        private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        override val events: Flow<PlayerEvent> = _events
        val held = MutableStateFlow(false)

        override fun hold(held: Boolean) {
            this.held.value = held
        }

        /** Waits for [subscribers] collectors first: the flow has no replay. */
        suspend fun emit(event: PlayerEvent, subscribers: Int = 1) {
            withTimeoutOrNull(TIMEOUT_MS) { _events.subscriptionCount.first { it >= subscribers } }
                ?: fail("$subscribers collectors of the player events not subscribed within $TIMEOUT_MS ms")
            _events.emit(event)
        }
    }

    private class RecordingClient : StubServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        override val isReadyForCommands = MutableStateFlow(true)
        val sent = Channel<Request>(Channel.UNLIMITED)
        val recoveries = MutableStateFlow(0)

        override suspend fun sendRequest(request: Request): Result<Answer> {
            sent.send(request)
            return Result.success(Answer(buildJsonObject {}))
        }

        suspend fun nextSent(): Request =
            withTimeoutOrNull(TIMEOUT_MS) { sent.receive() } ?: fail("no request sent within $TIMEOUT_MS ms")

        override fun requestCommandRecovery() {
            recoveries.value++
        }
    }

    private companion object {
        val PAUSE = APICommands.playersCmd("pause")
        val PLAY = APICommands.playersCmd("play")
        val NEXT = APICommands.playersCmd("next")
        val STOP = APICommands.playersCmd("stop")
        const val QUEUE_ID = "queue"
        const val EPISODE_POSITION_SEC = 1800.0
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val SETTLE_MS = 300L
    }
}
