package io.music_assistant.client.data.play

import io.music_assistant.client.data.model.client.QueueOption
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayCoalescingTest {
    @Test
    fun `a request on an idle queue waits alone`() {
        assertEquals(listOf(playA), coalesce(emptyList(), null, playA))
    }

    @Test
    fun `a repeat of the request in progress is dropped`() {
        assertEquals(emptyList(), coalesce(emptyList(), playA, playA))
    }

    @Test
    fun `a newer play replaces the waiting play`() {
        assertEquals(listOf(playC), coalesce(listOf(playB), playA, playC))
    }

    @Test
    fun `play and replace replace each other`() {
        val replaceC = playC.copy(option = QueueOption.REPLACE)
        assertEquals(listOf(replaceC), coalesce(listOf(playB), playA, replaceC))
        assertEquals(listOf(playB), coalesce(listOf(replaceC), playA, playB))
    }

    @Test
    fun `tapping the item in progress again cancels the waiting play`() {
        assertEquals(emptyList(), coalesce(listOf(playB), playA, playA))
    }

    @Test
    fun `a play after an add keeps the add and its order`() {
        val addC = request("c", QueueOption.ADD)
        assertEquals(listOf(playB, addC, playD), coalesce(listOf(playB, addC), playA, playD))
    }

    @Test
    fun `add and next are never replaced`() {
        val addC = request("c", QueueOption.ADD)
        val nextD = request("d", QueueOption.NEXT)
        assertEquals(listOf(addC, nextD), coalesce(listOf(addC), playA, nextD))
        assertEquals(listOf(addC, playB), coalesce(listOf(addC), playA, playB))
    }

    @Test
    fun `replace next stages like add and next so it is never replaced`() {
        val replaceNextC = request("c", QueueOption.REPLACE_NEXT)
        assertEquals(listOf(replaceNextC, playB), coalesce(listOf(replaceNextC), playA, playB))
        assertEquals(listOf(playB, replaceNextC), coalesce(listOf(playB), playA, replaceNextC))
    }

    @Test
    fun `the same media shuffled or from the beginning is a new request`() {
        assertEquals(listOf(playA.copy(shuffle = true)), coalesce(emptyList(), playA, playA.copy(shuffle = true)))
        assertEquals(
            listOf(playA.copy(startFromBeginning = true)),
            coalesce(emptyList(), playA, playA.copy(startFromBeginning = true)),
        )
    }

    @Test
    fun `a repeated add is a double tap`() {
        val addC = request("c", QueueOption.ADD)
        assertEquals(listOf(addC), coalesce(listOf(addC), playA, addC))
        assertEquals(emptyList(), coalesce(emptyList(), addC, addC))
    }

    @Test
    fun `the same media with another option is a new request`() {
        val addA = request("a", QueueOption.ADD)
        assertEquals(listOf(addA), coalesce(emptyList(), playA, addA))
    }

    @Test
    fun `the same media with another start item is a new request`() {
        val fromTrack = playA.copy(startItem = "track-7")
        assertEquals(listOf(fromTrack), coalesce(emptyList(), playA, fromTrack))
    }

    private companion object {
        fun request(media: String, option: QueueOption = QueueOption.PLAY) =
            PlayRequest(queueOrPlayerId = "queue", media = listOf(media), option = option)

        val playA = request("a")
        val playB = request("b")
        val playC = request("c")
        val playD = request("d")
    }
}
