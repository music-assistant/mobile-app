package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.PlayerDataFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayersStateDataTest {
    @Test
    fun `selectedPlayer returns the selected player`() {
        val first = PlayerDataFixtures.playerData(name = "First")
        val second = PlayerDataFixtures.playerData(name = "Second")
        val data = HomeScreenViewModel.PlayersState.Data(
            playerData = listOf(first, second),
            selectedPlayerIndex = 1,
        )

        assertEquals(second, data.selectedPlayer)
    }

    @Test
    fun `selectedPlayer is null without a valid selection`() {
        val player = PlayerDataFixtures.playerData()

        assertNull(HomeScreenViewModel.PlayersState.Data(listOf(player)).selectedPlayer)
        assertNull(
            HomeScreenViewModel.PlayersState.Data(
                playerData = listOf(player),
                selectedPlayerIndex = 1,
            ).selectedPlayer,
        )
    }

    // Issue #1053: a player leaving ahead of the selected one must not move the selection
    // onto whichever player shifts into the old slot.
    @Test
    fun `index follows the selected player when an earlier player leaves`() {
        val local = PlayerDataFixtures.playerData(name = "Local")
        val kitchen = PlayerDataFixtures.playerData(name = "Kitchen")
        val office = PlayerDataFixtures.playerData(name = "Office")

        assertEquals(1, listOf(local, kitchen, office).indexOfPlayer(kitchen.playerId))
        assertEquals(0, listOf(kitchen, office).indexOfPlayer(kitchen.playerId))
    }

    @Test
    fun `index is null when the selected player is not in the list`() {
        val players = listOf(PlayerDataFixtures.playerData(), PlayerDataFixtures.playerData())

        assertNull(players.indexOfPlayer("missing"))
        assertNull(players.indexOfPlayer(null))
    }
}
