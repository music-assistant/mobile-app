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
}
