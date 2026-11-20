package io.music_assistant.client.ui.compose.home.players.share

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures.playerData
import io.music_assistant.client.data.share.DeviceAudioShareState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAudioShareUiTest {
    private fun PlayerData.playing(isPlaying: Boolean) = copy(player = player.copy(isPlaying = isPlaying))

    private val device = playerData(queueId = "device").playing(false).copy(isLocal = true)
    private val kitchen = playerData(queueId = "kitchen")

    private fun ui(
        state: DeviceAudioShareState = DeviceAudioShareState.Idle,
        players: List<PlayerData> = listOf(device, kitchen),
        available: Boolean = true,
    ) = DeviceAudioShareUi.of(available, state, "sendspin://audio_source/device", players)

    @Test
    fun `a remote player is a target and the device is not`() {
        val ui = ui()

        assertTrue(ui.canStartOn(kitchen))
        assertFalse(ui.canStartOn(device))
        assertFalse(ui.blockedByLocalPlayback)
    }

    @Test
    fun `a group that has the device is not a target`() {
        val member = PlayerData.ChildBind(
            id = device.playerId,
            parentId = kitchen.playerId,
            volume = null,
            volumeSliderAccessible = false,
            isMuted = null,
            name = "Device",
            isBound = true,
            isManageable = true,
        )
        val group = kitchen.copy(childrenBinds = listOf(member))

        assertFalse(ui(players = listOf(device, group)).canStartOn(group))
    }

    @Test
    fun `playback on the device blocks the start`() {
        assertTrue(ui(players = listOf(device.playing(true), kitchen)).blockedByLocalPlayback)
        assertTrue(ui(players = listOf(device.copy(playRequestPending = true), kitchen)).blockedByLocalPlayback)
    }

    @Test
    fun `nothing starts while unavailable or while a share runs`() {
        assertFalse(ui(available = false).canStartOn(kitchen))
        assertFalse(ui(DeviceAudioShareState.Starting("kitchen")).canStartOn(kitchen))
    }

    @Test
    fun `a starting share marks the queue that the user picked`() {
        val ui = ui(DeviceAudioShareState.Starting("kitchen"))

        assertTrue(ui.sharing)
        assertEquals(kitchen.playerId, ui.target?.playerId)
    }

    @Test
    fun `an active share has no mark until a player plays the source`() {
        assertNull(ui(DeviceAudioShareState.Active).target)
    }

    @Test
    fun `an active share marks the player whose active source is the device`() {
        val onPhone = kitchen.copy(player = kitchen.player.copy(activeSource = "sendspin://audio_source/device"))
        val onOtherInput = kitchen.copy(player = kitchen.player.copy(activeSource = "vban://audio_source/desk"))

        assertEquals(onPhone.playerId, ui(DeviceAudioShareState.Active, listOf(device, onPhone)).target?.playerId)
        assertNull(ui(DeviceAudioShareState.Active, listOf(device, onOtherInput)).target)
    }
}
