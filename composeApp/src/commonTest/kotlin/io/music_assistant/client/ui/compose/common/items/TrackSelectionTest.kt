package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.testTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackSelectionTest {
    private val first = testTrack().copy(itemId = "1", uri = "library://track/1")
    private val second = testTrack().copy(itemId = "2", uri = "library://track/2")
    private val third = testTrack().copy(itemId = "3", uri = "library://track/3")

    @Test
    fun `start selects the track and activates the mode`() {
        val selection = TrackSelection()
        assertFalse(selection.isActive)

        selection.start(first)

        assertTrue(selection.isActive)
        assertEquals(listOf(first), selection.tracks)
    }

    @Test
    fun `tracks keep the order of selection`() {
        val selection = TrackSelection()
        selection.start(third)
        selection.toggle(first)
        selection.toggle(second)

        assertEquals(listOf(third, first, second), selection.tracks)
    }

    @Test
    fun `toggle deselects a selected track by uri`() {
        val selection = TrackSelection()
        selection.start(first)
        selection.toggle(second)

        selection.toggle(first.copy(name = "same uri"))

        assertEquals(listOf(second), selection.tracks)
        assertFalse(selection.isSelected(first))
    }

    @Test
    fun `deselecting the last track ends the mode`() {
        val selection = TrackSelection()
        selection.start(first)

        selection.toggle(first)

        assertFalse(selection.isActive)
    }

    @Test
    fun `start replaces a previous selection`() {
        val selection = TrackSelection()
        selection.start(first)
        selection.toggle(second)

        selection.start(third)

        assertEquals(listOf(third), selection.tracks)
    }

    @Test
    fun `a track without uri or not playable is not selectable`() {
        val selection = TrackSelection()
        val noUri = testTrack().copy(uri = null)
        val notPlayable = testTrack(isPlayable = false).copy(uri = "library://track/9")

        selection.start(noUri)
        assertFalse(selection.isActive)

        selection.start(first)
        selection.toggle(notPlayable)

        assertEquals(listOf(first), selection.tracks)
    }

    @Test
    fun `clear ends the mode`() {
        val selection = TrackSelection()
        selection.start(first)
        selection.toggle(second)

        selection.clear()

        assertFalse(selection.isActive)
        assertTrue(selection.tracks.isEmpty())
    }

    @Test
    fun `select all selects the candidates in list order`() {
        val selection = TrackSelection()
        selection.offer(listOf(first, second, third))
        selection.start(third)
        assertTrue(selection.canSelectAll)

        selection.selectAll()

        assertEquals(listOf(first, second, third), selection.tracks)
        assertFalse(selection.canSelectAll)
    }

    @Test
    fun `a withdrawn list stops being the candidates`() {
        val selection = TrackSelection()
        val shown = listOf(first, second)
        selection.offer(shown)

        selection.withdraw(listOf(first))
        assertEquals(shown, selection.candidates)

        selection.withdraw(shown)
        assertTrue(selection.candidates.isEmpty())
    }

    @Test
    fun `a position key keeps two copies of one track apart`() {
        val selection = TrackSelection(SelectionKey.POSITION)
        val copy1 = first.copy(position = 1)
        val copy2 = first.copy(position = 4)

        selection.start(copy1)
        assertFalse(selection.isSelected(copy2))
        selection.toggle(copy2)

        assertEquals(listOf(copy1, copy2), selection.tracks)
        assertEquals(listOf(copy1, copy2), selection.selectableOf(listOf(copy1, copy2)))
    }

    @Test
    fun `a position key needs a position`() {
        val selection = TrackSelection(SelectionKey.POSITION)

        selection.start(first.copy(position = null))

        assertFalse(selection.isActive)
    }
}
