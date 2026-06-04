package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemKindTest {
    @Test
    fun `itemKind maps each playable and browsable subtype`() {
        assertEquals(ItemKind.TRACK, testTrack().itemKind())
        assertEquals(ItemKind.RADIO, testRadio().itemKind())
        assertEquals(ItemKind.PODCAST_EPISODE, testPodcastEpisode().itemKind())
        assertEquals(ItemKind.ALBUM, testAlbum().itemKind())
        assertEquals(ItemKind.ARTIST, testArtist().itemKind())
        assertEquals(ItemKind.PLAYLIST, testPlaylist().itemKind())
        assertEquals(ItemKind.PODCAST, testPodcast().itemKind())
        assertEquals(ItemKind.AUDIOBOOK, testAudiobook().itemKind())
    }

    @Test
    fun `genre has no kind`() {
        assertNull(testGenre().itemKind())
    }

    @Test
    fun `track appears in every list context but not the detail button`() {
        ClickContext.entries.filter { it != ClickContext.DETAIL }.forEach {
            assertEquals(true, ItemKind.TRACK.appearsIn(it), "TRACK should appear in $it")
        }
        assertEquals(false, ItemKind.TRACK.appearsIn(ClickContext.DETAIL))
    }

    @Test
    fun `radio appears only where radio stations are listed`() {
        assertEquals(
            setOf(ClickContext.HOME, ClickContext.LIBRARY, ClickContext.SEARCH),
            ClickContext.entries.filter { ItemKind.RADIO.appearsIn(it) }.toSet(),
        )
    }

    @Test
    fun `podcast episode appears only in home and search`() {
        assertEquals(
            setOf(ClickContext.HOME, ClickContext.SEARCH),
            ClickContext.entries.filter { ItemKind.PODCAST_EPISODE.appearsIn(it) }.toSet(),
        )
    }

    @Test
    fun `browsable kinds appear only on the detail button`() {
        listOf(ItemKind.ALBUM, ItemKind.ARTIST, ItemKind.PLAYLIST, ItemKind.PODCAST, ItemKind.AUDIOBOOK)
            .forEach { kind ->
                assertEquals(
                    listOf(ClickContext.DETAIL),
                    ClickContext.entries.filter { kind.appearsIn(it) },
                    "$kind should appear only on DETAIL",
                )
            }
    }
}
