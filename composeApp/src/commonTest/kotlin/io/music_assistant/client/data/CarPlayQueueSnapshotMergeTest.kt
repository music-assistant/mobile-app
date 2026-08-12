package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class CarPlayQueueSnapshotMergeTest {
    private fun queue(id: String, stamp: Double, elapsed: Double) = QueueInfo(
        id = id,
        available = true,
        currentIndex = null,
        shuffleEnabled = false,
        repeatMode = RepeatMode.OFF,
        elapsedTime = elapsed,
        elapsedTimeLastUpdated = stamp,
        currentItem = null,
        radioSource = emptyList(),
        autoPlayEnabled = false,
    )

    @Test
    fun `delayed full refresh cannot overwrite newer retained queue state`() {
        val retained = queue("local", stamp = 200.0, elapsed = 75.0)
        val delayed = queue("local", stamp = 100.0, elapsed = 20.0)

        assertEquals(listOf(retained), mergeFullQueueSnapshot(listOf(retained), listOf(delayed)))
    }

    @Test
    fun `full refresh accepts newer state and newly discovered queues`() {
        val retained = queue("local", stamp = 100.0, elapsed = 20.0)
        val fresh = queue("local", stamp = 200.0, elapsed = 75.0)
        val discovered = queue("kitchen", stamp = 50.0, elapsed = 10.0)

        assertEquals(
            listOf(fresh, discovered),
            mergeFullQueueSnapshot(listOf(retained), listOf(fresh, discovered)),
        )
    }
}
