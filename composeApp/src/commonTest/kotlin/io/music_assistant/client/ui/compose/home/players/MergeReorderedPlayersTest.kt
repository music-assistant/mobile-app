package io.music_assistant.client.ui.compose.home.players

import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReorderedPlayersTest {
    private val full = listOf("a", "b", "c", "d")

    @Test
    fun nothingHiddenSavesTheNewOrder() {
        assertEquals(listOf("d", "a", "b", "c"), mergeReorderedPlayers(full, listOf("d", "a", "b", "c"), "d"))
    }

    @Test
    fun hiddenPlayerMovesWithThePlayerAfterIt() {
        assertEquals(listOf("d", "a", "b", "c"), mergeReorderedPlayers(full, listOf("d", "a", "c"), "d"))
        assertEquals(listOf("c", "a", "b", "d"), mergeReorderedPlayers(full, listOf("c", "a", "d"), "c"))
    }

    @Test
    fun hiddenPlayerAtTheStartMovesWithTheFirstShownPlayer() {
        assertEquals(
            listOf("d", "a", "b", "c"),
            mergeReorderedPlayers(full, listOf("d", "b", "c"), "d"),
        )
    }

    @Test
    fun hiddenPlayersAtTheEndStayAtTheEnd() {
        assertEquals(listOf("b", "a", "c", "d"), mergeReorderedPlayers(full, listOf("b", "a"), "b"))
    }

    @Test
    fun consecutiveHiddenPlayersKeepTheirOrder() {
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("e", "a", "b", "c", "d"), mergeReorderedPlayers(five, listOf("e", "a", "d"), "e"))
    }
}
