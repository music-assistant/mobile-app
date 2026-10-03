package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.server.ServerAiRadioHost
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueDjMenuTest {
    private val zed = ServerAiRadioHost(id = "h-zed", name = "zed")
    private val ann = ServerAiRadioHost(id = "h-ann", name = "Ann")

    private fun running(queueId: String, status: String = "running") =
        ServerAiRadioSession(sessionId = "x", stationId = "s-1", queueId = queueId, status = status)

    @Test
    fun hostsAreSortedByNameIgnoringCase() {
        val menu = QueueDjState(hosts = listOf(zed, ann)).menuFor("q")

        assertEquals(QueueDjMenu.Choices(listOf(ann, zed), selectedHostId = null), menu)
    }

    @Test
    fun theAssignedHostOfThisQueueIsSelected() {
        val state = QueueDjState(hosts = listOf(ann, zed), assignments = mapOf("q" to zed.id, "other" to ann.id))

        assertEquals(zed.id, (state.menuFor("q") as QueueDjMenu.Choices).selectedHostId)
    }

    @Test
    fun aRunningShowOnThisQueueIsOnAir() {
        val state = QueueDjState(hosts = listOf(ann), assignments = mapOf("q" to ann.id), running = running("q"))

        assertEquals(QueueDjMenu.OnAir, state.menuFor("q"))
    }

    @Test
    fun aShowOnAnotherQueueOrNotRunningLeavesTheChoices() {
        val elsewhere = QueueDjState(hosts = listOf(ann), running = running("other"))
        val finished = QueueDjState(hosts = listOf(ann), running = running("q", status = "finished"))

        assertEquals(QueueDjMenu.Choices(listOf(ann), null), elsewhere.menuFor("q"))
        assertEquals(QueueDjMenu.Choices(listOf(ann), null), finished.menuFor("q"))
    }
}
