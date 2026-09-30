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
        // Playlist order is the playlist's meaning, so no other field is offered. Removal sends
        // Track.position, so this is a UX choice rather than a correctness constraint.
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

    @Test
    fun `original keeps the delivered order outside album and playlist tracks`() {
        // Provider order is the point of Top tracks, so ORIGINAL must not re-sort by track number.
        val t = { track: Int, id: String -> testTrack().copy(itemId = id, discNumber = 1, trackNumber = track) }
        val delivered = listOf(t(3, "a"), t(1, "b"), t(2, "c"))
        val sorted = { descending: Boolean ->
            delivered.clientSorted(SortOption(SortField.ORIGINAL, descending), SubItemContext.ARTIST_TOP_TRACKS)
                .map { it.itemId }
        }
        assertEquals(listOf("a", "b", "c"), sorted(false))
        assertEquals(listOf("c", "b", "a"), sorted(true))
    }

    @Test
    fun `artist view all lists default to original and are user sortable`() {
        listOf(
            SubItemContext.ARTIST_TOP_TRACKS,
            SubItemContext.ARTIST_ALL_ALBUMS,
            SubItemContext.ARTIST_LIBRARY_ALBUMS,
        ).forEach {
            assertEquals(SortOption(SortField.ORIGINAL), SortConfig.defaultFor(it))
            assertTrue(SortConfig.isUserSortable(it))
        }
    }

    @Test
    fun `artist view all lists offer only fields that sort on the client`() {
        assertEquals(
            listOf(SortField.ORIGINAL, SortField.NAME, SortField.DURATION),
            SortConfig.fieldsFor(SubItemContext.ARTIST_TOP_TRACKS),
        )
        val albumFields = listOf(SortField.ORIGINAL, SortField.NAME, SortField.ARTIST_NAME, SortField.YEAR)
        assertEquals(albumFields, SortConfig.fieldsFor(SubItemContext.ARTIST_ALL_ALBUMS))
        assertEquals(albumFields, SortConfig.fieldsFor(SubItemContext.ARTIST_LIBRARY_ALBUMS))
    }

    // The server silently drops unknown order_by keys (no ORDER BY at all), so a wrong key
    // looks like "sort does nothing" rather than an error.
    @Test
    fun `artist sort uses the server album artist key`() {
        assertEquals("album_artist_name", SortOption(SortField.ARTIST_NAME).toServerString())
        assertEquals("album_artist_name_desc", SortOption(SortField.ARTIST_NAME, descending = true).toServerString())
    }
}
