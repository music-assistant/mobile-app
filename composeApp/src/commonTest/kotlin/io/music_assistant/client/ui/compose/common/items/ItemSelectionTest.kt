package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.testAudiobook
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testRadio
import io.music_assistant.client.data.model.client.testTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemSelectionTest {
    private val first = testTrack().copy(itemId = "1", uri = "library://track/1")
    private val second = testTrack().copy(itemId = "2", uri = "library://track/2")
    private val third = testTrack().copy(itemId = "3", uri = "library://track/3")

    @Test
    fun `start selects the track and activates the mode`() {
        val selection = ItemSelection()
        assertFalse(selection.isActive)

        selection.start(first)

        assertTrue(selection.isActive)
        assertEquals(listOf(first), selection.items)
    }

    @Test
    fun `tracks keep the order of selection`() {
        val selection = ItemSelection()
        selection.start(third)
        selection.toggle(first)
        selection.toggle(second)

        assertEquals(listOf(third, first, second), selection.items)
    }

    @Test
    fun `toggle deselects a selected track by uri`() {
        val selection = ItemSelection()
        selection.start(first)
        selection.toggle(second)

        selection.toggle(first.copy(name = "same uri"))

        assertEquals(listOf(second), selection.items)
        assertFalse(selection.isSelected(first))
    }

    @Test
    fun `deselecting the last track ends the mode`() {
        val selection = ItemSelection()
        selection.start(first)

        selection.toggle(first)

        assertFalse(selection.isActive)
    }

    @Test
    fun `start replaces a previous selection`() {
        val selection = ItemSelection()
        selection.start(first)
        selection.toggle(second)

        selection.start(third)

        assertEquals(listOf(third), selection.items)
    }

    @Test
    fun `a track without uri or not playable is not selectable`() {
        val selection = ItemSelection()
        val noUri = testTrack().copy(uri = null)
        val notPlayable = testTrack(isPlayable = false).copy(uri = "library://track/9")

        selection.start(noUri)
        assertFalse(selection.isActive)

        selection.start(first)
        selection.toggle(notPlayable)

        assertEquals(listOf(first), selection.items)
    }

    @Test
    fun `clear ends the mode`() {
        val selection = ItemSelection()
        selection.start(first)
        selection.toggle(second)

        selection.clear()

        assertFalse(selection.isActive)
        assertTrue(selection.items.isEmpty())
    }

    @Test
    fun `select all selects the candidates in list order`() {
        val selection = ItemSelection()
        selection.offer(listOf(first, second, third))
        selection.start(third)
        assertTrue(selection.canSelectAll)

        selection.selectAll()

        assertEquals(listOf(first, second, third), selection.items)
        assertFalse(selection.canSelectAll)
    }

    @Test
    fun `a withdrawn list stops being the candidates`() {
        val selection = ItemSelection()
        val shown = listOf(first, second)
        selection.offer(shown)

        selection.withdraw(listOf(first))
        assertEquals(shown, selection.candidates)

        selection.withdraw(shown)
        assertTrue(selection.candidates.isEmpty())
    }

    @Test
    fun `a position key keeps two copies of one track apart`() {
        val selection = ItemSelection(SelectionKey.POSITION)
        val copy1 = first.copy(position = 1)
        val copy2 = first.copy(position = 4)

        selection.start(copy1)
        assertFalse(selection.isSelected(copy2))
        selection.toggle(copy2)

        assertEquals(listOf(copy1, copy2), selection.items)
        assertEquals(listOf(copy1, copy2), selection.selectableOf(listOf(copy1, copy2)))
    }

    @Test
    fun `a position key needs a position`() {
        val selection = ItemSelection(SelectionKey.POSITION)

        selection.start(first.copy(position = null))

        assertFalse(selection.isActive)
    }

    @Test
    fun `tracks and episodes share one selection`() {
        val selection = ItemSelection()
        val episode = testPodcastEpisode().copy(itemId = "e", uri = "library://podcast_episode/e")

        selection.start(first)
        selection.toggle(episode)

        assertEquals(listOf(first, episode), selection.items)
    }

    @Test
    fun `a radio station or an audiobook is not selectable`() {
        val selection = ItemSelection()
        val radio = testRadio().copy(uri = "library://radio/r")
        val audiobook = testAudiobook().copy(uri = "library://audiobook/a")

        assertFalse(selection.canSelect(radio))
        assertFalse(selection.canSelect(audiobook))
        assertEquals(listOf(first), selection.selectableOf(listOf(radio, first, audiobook)))
    }
}
