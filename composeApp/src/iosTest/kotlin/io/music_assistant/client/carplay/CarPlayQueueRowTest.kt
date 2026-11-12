package io.music_assistant.client.carplay

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.home.buildDisplayRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CarPlayQueueRowTest {
    private val song = with(PlayerDataFixtures) { AppMediaItemFixtures.track(name = "Song").toQueueTrack(id = "s0") }

    @Test
    fun onlyTheCurrentQueueItemIsMarkedCurrent() {
        val next = with(PlayerDataFixtures) { AppMediaItemFixtures.track(name = "Next").toQueueTrack(id = "s1") }

        val rows = listOf(song, next).buildDisplayRows(currentItemId = "s0")
            .mapNotNull { it.toCarPlayQueueRow(currentItemId = "s0") }

        assertEquals(listOf(true, false), rows.map { assertIs<CarPlayQueueRow.Item>(it).isCurrent })
        assertEquals(listOf("queue:s0", "queue:s1"), rows.map { it.key })
    }

    @Test
    fun aChapterRowCarriesItsTitleAndAnOpenEndedChapterHasNoDuration() {
        val book = AppMediaItemFixtures.audiobook(chapters = listOf("A", "B")).copy(
            chapters = listOf(
                Chapter(position = 0, name = "A", start = 0.0, end = 90.0),
                Chapter(position = 1, name = "B", start = 90.0, end = null),
            ),
        )
        val track = with(PlayerDataFixtures) { book.toQueueTrack(id = "b1") }

        val chapters = listOf(track).buildDisplayRows(currentItemId = "b1")
            .mapNotNull { it.toCarPlayQueueRow(currentItemId = "b1") }
            .drop(1)
            .map { assertIs<CarPlayQueueRow.Chapter>(it) }

        assertEquals(listOf("A", "B"), chapters.map { it.title })
        assertEquals("01:30", chapters[0].duration)
        assertNull(chapters[1].duration)
    }
}
