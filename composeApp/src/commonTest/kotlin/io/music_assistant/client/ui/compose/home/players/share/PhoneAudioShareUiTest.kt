package io.music_assistant.client.ui.compose.home.players.share

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures.playerData
import io.music_assistant.client.data.share.PhoneAudioShareState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneAudioShareUiTest {
    private fun PlayerData.playing(isPlaying: Boolean) = copy(player = player.copy(isPlaying = isPlaying))

    private val phone = playerData(queueId = "phone").playing(false).copy(isLocal = true)
    private val kitchen = playerData(queueId = "kitchen")

    private fun ui(
        state: PhoneAudioShareState = PhoneAudioShareState.Idle,
        players: List<PlayerData> = listOf(phone, kitchen),
        available: Boolean = true,
    ) = PhoneAudioShareUi.of(available, state, "sendspin://audio_source/phone", players)

    @Test
    fun `a remote player is a target and the phone is not`() {
        val ui = ui()

        assertTrue(ui.canStartOn(kitchen))
        assertFalse(ui.canStartOn(phone))
        assertFalse(ui.blockedByLocalPlayback)
    }

    @Test
    fun `a group that has the phone is not a target`() {
        val member = PlayerData.ChildBind(
            id = phone.playerId,
            parentId = kitchen.playerId,
            volume = null,
            volumeSliderAccessible = false,
            isMuted = null,
            name = "Phone",
            isBound = true,
            isManageable = true,
        )
        val group = kitchen.copy(childrenBinds = listOf(member))

        assertFalse(ui(players = listOf(phone, group)).canStartOn(group))
    }

    @Test
    fun `playback on the phone blocks the start`() {
        assertTrue(ui(players = listOf(phone.playing(true), kitchen)).blockedByLocalPlayback)
        assertTrue(ui(players = listOf(phone.copy(playRequestPending = true), kitchen)).blockedByLocalPlayback)
    }

    @Test
    fun `nothing starts while unavailable or while a share runs`() {
        assertFalse(ui(available = false).canStartOn(kitchen))
        assertFalse(ui(PhoneAudioShareState.Starting("kitchen")).canStartOn(kitchen))
    }

    @Test
    fun `a starting share marks the queue that the user picked`() {
        val ui = ui(PhoneAudioShareState.Starting("kitchen"))

        assertTrue(ui.sharing)
        assertEquals(kitchen.playerId, ui.target?.playerId)
    }

    @Test
    fun `an active share has no mark until a player plays the source`() {
        assertNull(ui(PhoneAudioShareState.Active).target)
    }

    @Test
    fun `an active share marks the player whose active source is the phone`() {
        val onPhone = kitchen.copy(player = kitchen.player.copy(liveInputUri = "sendspin://audio_source/phone"))
        val onOtherInput = kitchen.copy(player = kitchen.player.copy(liveInputUri = "vban://audio_source/desk"))

        assertEquals(onPhone.playerId, ui(PhoneAudioShareState.Active, listOf(phone, onPhone)).target?.playerId)
        assertNull(ui(PhoneAudioShareState.Active, listOf(phone, onOtherInput)).target)
    }
}
