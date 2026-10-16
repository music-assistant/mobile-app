package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.server.ServerAiRadioHost
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueDjMenuTest {
    private val zed = ServerAiRadioHost(id = "h-zed", name = "zed")
    private val ann = ServerAiRadioHost(id = "h-ann", name = "Ann")

    @Test
    fun hostsAreSortedByNameIgnoringCase() {
        val menu = QueueDjState(hosts = listOf(zed, ann)).menuFor("q")

        assertEquals(QueueDjMenu(listOf(ann, zed), selectedHostId = null), menu)
    }

    @Test
    fun theAssignedHostOfThisQueueIsSelected() {
        val state = QueueDjState(hosts = listOf(ann, zed), assignments = mapOf("q" to zed.id, "other" to ann.id))

        assertEquals(zed.id, state.menuFor("q").selectedHostId)
    }
}
