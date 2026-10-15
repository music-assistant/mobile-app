package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures.playerData
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayRequestsPendingTest {
    private val kitchen = playerData(queueId = "kitchen")
    private val office = playerData(queueId = "office")

    private fun serverQueue(id: String, playActionInProgress: Boolean) = QueueInfo(
        id = id,
        available = true,
        currentIndex = null,
        shuffleEnabled = false,
        repeatMode = RepeatMode.OFF,
        elapsedTime = null,
        elapsedTimeLastUpdated = null,
        currentItem = null,
        radioSource = emptyList(),
        autoPlayEnabled = false,
        playActionInProgress = playActionInProgress,
    )

    private fun List<PlayerData>.pending() = map { it.playRequestPending }

    @Test
    fun `a request of this app marks only its queue`() {
        val result = listOf(kitchen, office).withPlayRequestsPending(setOf("kitchen"), emptyList())

        assertEquals(listOf(true, false), result.pending())
    }

    @Test
    fun `the server flag marks the queue even when the player queue info lacks it`() {
        // The local player's PlayerData carries LocalPlayerAdapter's queue info, never the flag.
        val queues = listOf(serverQueue("kitchen", false), serverQueue("office", true))

        val result = listOf(kitchen, office).withPlayRequestsPending(emptySet(), queues)

        assertEquals(listOf(false, true), result.pending())
    }

    @Test
    fun `a cleared flag and no request of this app clear an earlier mark`() {
        val wasPending = kitchen.copy(playRequestPending = true)

        val result = listOf(wasPending).withPlayRequestsPending(emptySet(), listOf(serverQueue("kitchen", false)))

        assertEquals(listOf(false), result.pending())
    }
}
