package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the SortField.ORIGINAL contract that Android Auto's album/playlist drilldowns now rely on
 * (the convergence onto SortConfig.defaultFor + context-aware clientSorted).
 */
class SortOptionTest {
    @Test
    fun `default for album tracks is original ascending`() {
        assertEquals(SortOption(SortField.ORIGINAL), SortConfig.defaultFor(SubItemContext.ALBUM_TRACKS))
    }

    @Test
    fun `default for playlist tracks is original ascending`() {
        assertEquals(SortOption(SortField.ORIGINAL), SortConfig.defaultFor(SubItemContext.PLAYLIST_ITEMS))
    }

    @Test
    fun `original orders album tracks by disc then track number`() {
        val t = { disc: Int, track: Int, id: String ->
            testTrack().copy(itemId = id, discNumber = disc, trackNumber = track)
        }
        val shuffled = listOf(t(2, 1, "d2t1"), t(1, 2, "d1t2"), t(1, 1, "d1t1"))
        val sorted = shuffled.clientSorted(SortOption(SortField.ORIGINAL), SubItemContext.ALBUM_TRACKS)
        assertEquals(listOf("d1t1", "d1t2", "d2t1"), sorted.map { it.itemId })
    }

    @Test
    fun `original orders playlist tracks by position`() {
        val t = { pos: Int, id: String -> testTrack().copy(itemId = id, position = pos) }
        // Same disc/track numbers across the playlist — only position must drive the order.
        val shuffled = listOf(t(3, "p3"), t(1, "p1"), t(2, "p2"))
        val sorted = shuffled.clientSorted(SortOption(SortField.ORIGINAL), SubItemContext.PLAYLIST_ITEMS)
        assertEquals(listOf("p1", "p2", "p3"), sorted.map { it.itemId })
    }

    @Test
    fun `playlist items are not user sortable`() {
        // ActionsViewModel.removeFromPlaylist derives the server position from the displayed index,
        // which only holds while playlist items stay in ORIGINAL ascending order. Offering another
        // field here would make removal delete the wrong track.
        assertEquals(listOf(SortField.ORIGINAL), SortConfig.fieldsFor(SubItemContext.PLAYLIST_ITEMS))
        assertFalse(SortConfig.isUserSortable(SubItemContext.PLAYLIST_ITEMS))
    }

    @Test
    fun `album tracks are not user sortable`() {
        assertEquals(listOf(SortField.ORIGINAL), SortConfig.fieldsFor(SubItemContext.ALBUM_TRACKS))
        assertFalse(SortConfig.isUserSortable(SubItemContext.ALBUM_TRACKS))
    }

    @Test
    fun `podcast episodes stay user sortable`() {
        assertTrue(SortConfig.isUserSortable(SubItemContext.PODCAST_EPISODES))
    }
}
