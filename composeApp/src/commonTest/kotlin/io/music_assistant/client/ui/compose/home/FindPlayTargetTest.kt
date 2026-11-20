package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.PlayerDataFixtures.playerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindPlayTargetTest {
    private val bar = playerData(name = "Bar")
    private val phone = playerData(name = "My phone").copy(isLocal = true)
    private val players = listOf(bar, phone)

    @Test
    fun noPlayerMeansTheSelectedPlayer() {
        assertEquals(bar, players.findPlayTarget(null, selectedId = bar.playerId))
    }

    @Test
    fun noPlayerAndNoSelectionMatchesNothing() {
        assertNull(players.findPlayTarget(null, selectedId = null))
    }

    @Test
    fun localNamesTheBuiltInPlayer() {
        assertEquals(phone, players.findPlayTarget("LOCAL", selectedId = bar.playerId))
    }

    @Test
    fun localWithoutBuiltInPlayerMatchesNothing() {
        assertNull(listOf(bar).findPlayTarget("local", selectedId = bar.playerId))
    }

    @Test
    fun nameOrIdMatchesLikeThePlayersLink() {
        assertEquals(bar, players.findPlayTarget("bar", selectedId = phone.playerId))
        assertEquals(bar, players.findPlayTarget(bar.playerId, selectedId = phone.playerId))
    }
}
