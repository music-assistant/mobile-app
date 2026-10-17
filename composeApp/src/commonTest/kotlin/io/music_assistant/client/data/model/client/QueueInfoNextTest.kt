package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [nextMovesQueue] copies the rule of the server `player_queues/next`. */
class QueueInfoNextTest {
    private fun queue(currentIndex: Int?, itemCount: Int?, repeatMode: RepeatMode = RepeatMode.OFF) = QueueInfo(
        id = "q",
        available = true,
        currentIndex = currentIndex,
        shuffleEnabled = false,
        repeatMode = repeatMode,
        elapsedTime = null,
        elapsedTimeLastUpdated = null,
        currentItem = null,
        radioSource = emptyList(),
        autoPlayEnabled = false,
        itemCount = itemCount,
    )

    @Test
    fun theLastItemDoesNotMove() {
        assertFalse(queue(currentIndex = 0, itemCount = 1).nextMovesQueue)
        assertFalse(queue(currentIndex = 4, itemCount = 5, repeatMode = RepeatMode.ONE).nextMovesQueue)
    }

    @Test
    fun theLastItemMovesWithRepeatAll() {
        assertTrue(queue(currentIndex = 0, itemCount = 1, repeatMode = RepeatMode.ALL).nextMovesQueue)
    }

    @Test
    fun anItemBeforeTheLastMoves() {
        assertTrue(queue(currentIndex = 3, itemCount = 5).nextMovesQueue)
    }

    @Test
    fun noCurrentItemDoesNotMove() {
        assertFalse(queue(currentIndex = null, itemCount = 5).nextMovesQueue)
    }

    @Test
    fun anUnknownCountMoves() {
        assertTrue(queue(currentIndex = 0, itemCount = null).nextMovesQueue)
    }
}
