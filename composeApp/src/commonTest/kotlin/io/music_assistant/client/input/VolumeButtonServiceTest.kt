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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeButtonServiceTest {
    private class FakeTarget : RemoteVolumeTarget {
        override var selectedPlayer: PlayerData? = null
        override val isReadyForCommands = MutableStateFlow(true)
        val sent = mutableListOf<PlayerAction>()

        /** While set, each send suspends until it completes, like a server round trip. */
        var roundTrip: CompletableDeferred<Unit>? = null

        override suspend fun sendRemotePlayerAction(data: PlayerData, action: PlayerAction) {
            sent += action
            roundTrip?.await()
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
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
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
