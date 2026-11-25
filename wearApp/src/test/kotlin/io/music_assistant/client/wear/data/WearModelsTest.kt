package io.music_assistant.client.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearModelsTest {
    private fun player(
        available: Boolean = true,
        listed: Boolean = true,
        members: Int = 0,
    ) = WearPlayer(
        id = "kitchen",
        name = "Kitchen",
        isGroup = members > 0,
        memberCount = members,
        isAvailable = available,
        isListed = listed,
        isPlaying = false,
        queueId = "kitchen",
        canSetVolume = true,
        canNextPrevious = true,
        volume = 40,
        title = "Teardrop",
        artist = "Massive Attack",
        imageUrl = null,
        duration = 330.0,
    )

    private fun queue(ended: Boolean) = WearQueue(
        id = "kitchen",
        currentItemId = "item-2",
        currentIndex = 2,
        itemCount = 10,
        ended = ended,
        elapsedSeconds = 30.0,
        currentTitle = "Teardrop",
        currentArtist = "Massive Attack",
        currentImageUrl = null,
        currentDuration = 330.0,
    )

    @Test
    fun `a player with members commands its volume as a group`() {
        assertFalse(player().usesGroupVolume)
        assertTrue(player(members = 2).usesGroupVolume)
    }

    @Test
    fun `only a reachable and listed player can be selected`() {
        assertTrue(player().isSelectable)
        assertFalse(player(available = false).isSelectable)
        assertFalse(player(listed = false).isSelectable)
    }

    @Test
    fun `an ended queue marks no row as playing`() {
        assertEquals("item-2", queue(ended = false).activeItemId)
        assertNull(queue(ended = true).activeItemId)
    }

    @Test
    fun `progress is the fraction played, and null without a usable duration`() {
        val playing = NowPlaying(
            title = "Teardrop",
            artist = "Massive Attack",
            imageUrl = null,
            isPlaying = true,
            positionSeconds = 33.0,
            durationSeconds = 330.0,
        )
        assertEquals(0.1f, playing.progress)
        assertNull(playing.copy(durationSeconds = null).progress)
        // A live stream reports no length; a server that reports zero must not divide by it.
        assertNull(playing.copy(durationSeconds = 0.0).progress)
    }

    @Test
    fun `progress is clamped when the server reports past the end`() {
        val overrun = NowPlaying(
            title = "Teardrop",
            artist = null,
            imageUrl = null,
            isPlaying = true,
            positionSeconds = 400.0,
            durationSeconds = 330.0,
        )
        assertEquals(1f, overrun.progress)
    }
}
