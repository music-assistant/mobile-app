package io.music_assistant.client.input

import app.cash.turbine.test
import com.russhwolf.settings.MapSettings
import io.music_assistant.client.data.RemoteVolumeTarget
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.home.withPendingVolumes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VolumeButtonServiceTest {
    private class FakeTarget : RemoteVolumeTarget {
        override var selectedPlayer: PlayerData? = null
        override val isReadyForCommands = MutableStateFlow(true)
        val sent = mutableListOf<PlayerAction>()

        /** While set, each send suspends until it completes, like a server round trip. */
        var roundTrip: CompletableDeferred<Unit>? = null

        /** While set, each send fails with it, like a dropped connection. */
        var failure: Throwable? = null

        override suspend fun sendRemotePlayerAction(data: PlayerData, action: PlayerAction) {
            sent += action
            roundTrip?.await()
            failure?.let { throw it }
        }
    }

    private val settings = SettingsRepository(MapSettings(), MapSettings())
    private val target = FakeTarget()

    private fun service(scope: CoroutineScope) = VolumeButtonService(target, settings, scope)

    private fun TestScope.enabledServiceWith(player: PlayerData): VolumeButtonService {
        settings.setVolumeKeysControlSelectedPlayer(true)
        target.selectedPlayer = player
        return service(backgroundScope)
    }

    @Test
    fun buttonPressesAreEmitted() = runTest {
        val service = service(backgroundScope)

        service.buttonPresses.test {
            service.onPlatformVolumeButtonPressed()
            assertEquals(UnroutedVolumeKey.NOT_SUPPORTED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keyLeftToTheDeviceSaysWhy() = runTest {
        target.selectedPlayer = playerData()
        val service = service(backgroundScope)

        service.buttonPresses.test {
            assertFalse(service.onVolumeKey(up = true, isRepeat = false))
            assertEquals(UnroutedVolumeKey.SETTING_OFF, awaitItem())

            settings.setVolumeKeysControlSelectedPlayer(true)
            assertFalse(service.onVolumeKey(up = true, isRepeat = false, inCall = true))
            assertEquals(UnroutedVolumeKey.IN_CALL, awaitItem())

            // Repeats of a press give no second reason.
            assertFalse(service.onVolumeKey(up = true, isRepeat = true, inCall = true))
            // The local player: the device volume, with no toast.
            target.selectedPlayer = playerData(isLocal = true)
            assertFalse(service.onVolumeKey(up = true, isRepeat = false))
            expectNoEvents()
        }
        runCurrent()
        assertTrue(target.sent.isEmpty())
    }

    @Test
    fun settingOffLeavesTheKeyToTheDevice() = runTest {
        target.selectedPlayer = playerData()
        val service = service(backgroundScope)

        assertFalse(service.onVolumeKey(up = true, isRepeat = false))
        runCurrent()
        assertTrue(target.sent.isEmpty())
    }

    @Test
    fun localPlayerLeavesTheKeyToTheDevice() = runTest {
        val service = enabledServiceWith(playerData(isLocal = true))

        assertFalse(service.onVolumeKey(up = true, isRepeat = false))
        runCurrent()
        assertTrue(target.sent.isEmpty())
    }

    @Test
    fun playerWithoutVolumeControlTakesTheKeyAndGivesTheReason() = runTest {
        assertRefusal(playerData(canSetVolume = false), VolumeKeyRefusal.Reason.NO_VOLUME_CONTROL)
    }

    @Test
    fun poweredOffPlayerTakesTheKeyAndGivesTheReason() = runTest {
        assertRefusal(playerData(isAvailable = false), VolumeKeyRefusal.Reason.POWERED_OFF)
    }

    @Test
    fun sessionNotReadyTakesTheKeyAndGivesTheReason() = runTest {
        target.isReadyForCommands.value = false
        assertRefusal(playerData(), VolumeKeyRefusal.Reason.NOT_CONNECTED)
    }

    @Test
    fun repeatsOfARefusedPressGiveNoSecondReason() = runTest {
        val service = enabledServiceWith(playerData(canSetVolume = false))

        service.refusals.test {
            assertTrue(service.onVolumeKey(up = true, isRepeat = false))
            awaitItem()
            assertTrue(service.onVolumeKey(up = true, isRepeat = true))
            assertTrue(service.onVolumeKey(up = true, isRepeat = true))
            expectNoEvents()
        }
    }

    private suspend fun TestScope.assertRefusal(player: PlayerData, reason: VolumeKeyRefusal.Reason) {
        val service = enabledServiceWith(player)

        service.refusals.test {
            assertTrue(service.onVolumeKey(up = true, isRepeat = false))
            assertEquals(VolumeKeyRefusal(reason, "Kitchen"), awaitItem())
        }
        runCurrent()
        assertTrue(target.sent.isEmpty())
    }

    @Test
    fun remotePlayerGetsTheVolumeStep() = runTest {
        val service = enabledServiceWith(playerData())

        service.remoteAdjust.test {
            assertTrue(service.onVolumeKey(up = true, isRepeat = false))
            assertEquals("remote", awaitItem())
            runCurrent()
            assertTrue(service.onVolumeKey(up = false, isRepeat = false))
            assertEquals("remote", awaitItem())
            runCurrent()
        }
        assertEquals<List<PlayerAction>>(listOf(PlayerAction.VolumeUp, PlayerAction.VolumeDown), target.sent)
    }

    private fun VolumeButtonService.levels() = pendingVolumes.value.mapValues { it.value.level }

    @Test
    fun sentStepsShowThePendingLevelUntilTheServerHadTimeToReachIt() = runTest {
        val service = enabledServiceWith(playerData())

        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        service.onVolumeKey(up = true, isRepeat = true)
        runCurrent()
        // 40 → 43 → 46, stepping from the pending level, not the reported 40.
        assertEquals(mapOf("remote" to 46f), service.levels())

        advanceTimeBy(PENDING_VOLUME_MS - 1)
        service.onVolumeKey(up = false, isRepeat = false)
        runCurrent()
        assertEquals(mapOf("remote" to 43f), service.levels())

        // Each sent step restarts the hold.
        advanceTimeBy(PENDING_VOLUME_MS - 1)
        assertEquals(mapOf("remote" to 43f), service.levels())
        advanceTimeBy(2)
        assertTrue(service.levels().isEmpty())
    }

    @Test
    fun heldKeyShowsOnlyTheStepsThatReachTheServer() = runTest {
        val service = enabledServiceWith(playerData())
        val firstTrip = CompletableDeferred<Unit>()
        target.roundTrip = firstTrip

        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        assertEquals(mapOf("remote" to 43f), service.levels())

        // Three repeats while the first step is in flight: the channel keeps only the last.
        repeat(3) { service.onVolumeKey(up = true, isRepeat = true) }
        runCurrent()
        assertEquals(mapOf("remote" to 43f), service.levels())

        target.roundTrip = null
        firstTrip.complete(Unit)
        runCurrent()
        assertEquals(2, target.sent.size)
        assertEquals(mapOf("remote" to 46f), service.levels())
    }

    @Test
    fun failedStepShowsTheReportedLevelAgain() = runTest {
        val service = enabledServiceWith(playerData())
        target.failure = IllegalStateException("connection dropped")

        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        assertTrue(service.levels().isEmpty())

        // The next press starts again from the reported 40, and an old expiry doesn't touch it.
        target.failure = null
        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        assertEquals(mapOf("remote" to 43f), service.levels())
    }

    @Test
    fun pendingLevelsReplaceTheReportedVolume() {
        val single = playerData()
        val group = playerData().let { data ->
            data.copy(player = data.player.copy(id = "group", groupMembers = setOf("a", "b"), groupVolume = 20f))
        }
        val players = DataState.Data(listOf(single, group))

        assertSame(players, players.withPendingVolumes(emptyMap()))
        val shown = players.withPendingVolumes(
            mapOf("remote" to PendingVolume(43f, 1), "group" to PendingVolume(22f, 2)),
        ) as DataState.Data
        assertEquals(listOf(43f, 22f), shown.data.map { it.player.currentVolume })
        // A group shows its group volume; its own volume level stays as reported.
        assertEquals(40f, shown.data[1].player.volumeLevel)

        val onlySingle = players.withPendingVolumes(mapOf("remote" to PendingVolume(46f, 3))) as DataState.Data
        assertSame(group, onlySingle.data[1])
    }

    @Test
    fun refusedPressShowsNoPendingLevel() = runTest {
        val service = enabledServiceWith(playerData(canSetVolume = false))

        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        assertTrue(service.pendingVolumes.value.isEmpty())
    }

    @Test
    fun nextVolumeUsesTheServerSteps() {
        assertEquals(6f, nextVolume(5f, up = true))
        assertEquals(22f, nextVolume(20f, up = true))
        assertEquals(53f, nextVolume(50f, up = true))
        assertEquals(78f, nextVolume(80f, up = false))
        assertEquals(100f, nextVolume(100f, up = true))
        assertEquals(0f, nextVolume(0f, up = false))
    }

    @Test
    fun boundGroupGetsTheGroupVolumeStep() = runTest {
        val service = enabledServiceWith(playerData(boundChild = true))

        assertTrue(service.onVolumeKey(up = false, isRepeat = false))
        runCurrent()
        assertEquals<List<PlayerAction>>(listOf(PlayerAction.GroupVolumeDown), target.sent)
    }

    @Test
    fun heldKeySendsOneStepPerRoundTripAndTheLatestDirectionWins() = runTest {
        val service = enabledServiceWith(playerData())
        val first = CompletableDeferred<Unit>().also { target.roundTrip = it }

        service.onVolumeKey(up = true, isRepeat = false)
        runCurrent()
        service.onVolumeKey(up = true, isRepeat = true)
        service.onVolumeKey(up = true, isRepeat = true)
        service.onVolumeKey(up = false, isRepeat = false)
        runCurrent()
        assertEquals<List<PlayerAction>>(listOf(PlayerAction.VolumeUp), target.sent)

        target.roundTrip = null
        first.complete(Unit)
        runCurrent()
        assertEquals<List<PlayerAction>>(listOf(PlayerAction.VolumeUp, PlayerAction.VolumeDown), target.sent)
    }

    private fun playerData(
        isLocal: Boolean = false,
        canSetVolume: Boolean = true,
        isAvailable: Boolean = true,
        boundChild: Boolean = false,
    ) = PlayerData(
        player = Player(
            id = "remote",
            name = "Kitchen",
            provider = "sonos",
            type = PlayerType.PLAYER,
            isListed = true,
            isAvailable = isAvailable,
            needsSetup = false,
            canSetVolume = canSetVolume,
            canPower = false,
            isPowered = true,
            volumeLevel = 40f,
            volumeControl = null,
            volumeMuted = false,
            canMute = true,
            queueId = "remote",
            isPlaying = true,
            isAnnouncing = false,
            canGroupWith = null,
            groupMembers = null,
            staticGroupMembers = null,
            activeGroup = null,
            syncedTo = null,
            groupVolume = null,
            groupVolumeMuted = false,
            currentMedia = null,
        ),
        queue = DataState.NoData(),
        parentBind = null,
        childrenBinds = listOfNotNull(
            PlayerData.ChildBind(
                id = "child",
                parentId = "remote",
                name = "Hall",
                volume = 30f,
                volumeSliderAccessible = true,
                isMuted = false,
                isBound = true,
                isManageable = true,
            ).takeIf { boundChild },
        ),
        isLocal = isLocal,
    )
}
