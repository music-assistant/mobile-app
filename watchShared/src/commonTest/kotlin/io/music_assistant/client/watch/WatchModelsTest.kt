package io.music_assistant.client.watch

import io.music_assistant.client.watch.data.WatchNowPlaying
import io.music_assistant.client.watch.data.WatchPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchModelsTest {
    private fun player(
        isAvailable: Boolean = true,
        isListed: Boolean = true,
        memberCount: Int = 0,
        artist: String? = null,
        title: String? = null,
    ) = WatchPlayer(
        id = "p1",
        name = "Kitchen",
        isGroup = memberCount > 0,
        memberCount = memberCount,
        isAvailable = isAvailable,
        isListed = isListed,
        isPlaying = false,
        queueId = "p1",
        canSetVolume = true,
        canNextPrevious = true,
        volume = 40,
        title = title,
        artist = artist,
        imageUrl = null,
        duration = null,
    )

    @Test
    fun aHiddenOrUnreachablePlayerCannotBeChosen() {
        assertTrue(player().isSelectable)
        assertFalse(player(isListed = false).isSelectable)
        assertFalse(player(isAvailable = false).isSelectable)
    }

    @Test
    fun aGroupCommandsItsMembersVolumeTogether() {
        assertFalse(player().usesGroupVolume)
        assertTrue(player(memberCount = 2).usesGroupVolume)
    }

    @Test
    fun theSubtitlePrefersTheArtistAndFallsBackToTheTitle() {
        assertEquals("Nils Frahm", player(artist = "Nils Frahm", title = "Says").subtitle)
        assertEquals("Says", player(title = "Says").subtitle)
        assertNull(player().subtitle)
    }

    @Test
    fun progressIsClampedAndAbsentWithoutALength() {
        val track = WatchNowPlaying("Says", null, null, isPlaying = true, 30.0, 60.0)
        assertEquals(0.5f, track.progress)
        assertEquals(1f, track.progressAt(120.0))
        assertEquals(0f, track.progressAt(-5.0))
        assertNull(track.copy(durationSeconds = null).progress)
        assertNull(track.copy(durationSeconds = 0.0).progress)
    }
}
