package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The in-list filter (issue #1010) mirrors the web frontend: name, artist and album, case-insensitive. */
class QueryFilterTest {
    private fun track(
        id: String,
        name: String,
        artist: String? = null,
        album: String? = null,
        version: String? = null,
    ): Track = testTrack().copy(
        itemId = id,
        name = name,
        version = version,
        artists = listOfNotNull(artist?.let { testArtist().copy(name = it) }),
        album = album?.let { testAlbum().copy(name = it) },
    )

    private val tracks = listOf(
        track("1", "Paranoid Android", artist = "Radiohead", album = "OK Computer"),
        track("2", "Karma Police", artist = "Radiohead", album = "OK Computer"),
        track("3", "Everlong", artist = "Foo Fighters", album = "The Colour and the Shape"),
        track("4", "Intro", version = "Live at Reading"),
    )

    private fun ids(query: String) = tracks.clientFiltered(query).map { it.itemId }

    @Test
    fun `blank query returns the same list instance`() {
        assertSame(tracks, tracks.clientFiltered(""))
        assertSame(tracks, tracks.clientFiltered("   "))
    }

    @Test
    fun `matches the name case-insensitively`() {
        assertEquals(listOf("2"), ids("karma"))
    }

    @Test
    fun `matches the version through displayName`() {
        assertEquals(listOf("4"), ids("reading"))
    }

    @Test
    fun `matches an artist name`() {
        assertEquals(listOf("3"), ids("foo fighters"))
    }

    @Test
    fun `matches the album name`() {
        assertEquals(listOf("1", "2"), ids("ok computer"))
    }

    @Test
    fun `no match yields an empty list`() {
        assertEquals(emptyList(), ids("nirvana"))
    }

    @Test
    fun `query is trimmed`() {
        assertEquals(listOf("3"), ids("  everlong "))
    }

    @Test
    fun `keeps the input order`() {
        assertEquals(listOf("1", "2"), ids("radiohead"))
        assertEquals(listOf("2", "1"), tracks.reversed().clientFiltered("radiohead").map { it.itemId })
    }

    @Test
    fun `filters plain media items by displayName`() {
        val albums = listOf(
            testAlbum().copy(itemId = "a", name = "Abbey Road"),
            testAlbum().copy(itemId = "b", name = "Help!"),
        )
        assertEquals(listOf("b"), albums.clientFiltered("help").map { it.itemId })
    }
}
