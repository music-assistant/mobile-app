package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerSource
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The transport buttons of a player on a source that plays in place of its queue. */
class TransportStateTest {
    private fun onSource(source: PlayerSource, isPlaying: Boolean = true): PlayerData =
        PlayerDataFixtures.playerData().let {
            it.copy(
                player = it.player.copy(activeSource = source.id, sources = listOf(source), isPlaying = isPlaying),
                queue = DataState.NoData(),
            )
        }

    private fun PlayerData.transport() = transportState(chapterProgressEnabled = false)

    @Test
    fun `a source that can pause keeps Pause`() {
        val main = onSource(PlayerSource(id = URI, canPlayPause = true)).mainTransport()

        assertEquals(MainButton.PAUSE, main.button)
        assertEquals(PlayerAction.TogglePlayPause, main.action)
        assertTrue(main.enabled)
    }

    @Test
    fun `a source that cannot pause gets Stop while it plays`() {
        val main = onSource(PlayerSource(id = URI)).mainTransport()

        assertEquals(MainButton.STOP, main.button)
        assertEquals(PlayerAction.Stop, main.action)
        assertTrue(main.enabled)
    }

    @Test
    fun `a stopped source accepts Play unless it is passive`() {
        assertTrue(onSource(PlayerSource(id = URI), isPlaying = false).mainTransport().enabled)
        assertFalse(onSource(PlayerSource(id = URI, passive = true), isPlaying = false).mainTransport().enabled)
    }

    @Test
    fun `next and previous follow the flag of the source`() {
        val can = onSource(PlayerSource(id = URI, canNextPrevious = true)).transport()
        val cannot = onSource(PlayerSource(id = URI, canPlayPause = true)).transport()

        assertTrue(can.skipBackEnabled && can.skipForwardEnabled)
        assertFalse(cannot.skipBackEnabled || cannot.skipForwardEnabled)
    }

    @Test
    fun `a queue without a playable item disables the skip buttons`() {
        val transport = PlayerDataFixtures.playerData().transport()

        assertFalse(transport.buttonsEnabled)
        assertFalse(transport.skipBackEnabled || transport.skipForwardEnabled)
    }

    private companion object {
        const val URI = "spotify://audio_source/1"
    }
}
