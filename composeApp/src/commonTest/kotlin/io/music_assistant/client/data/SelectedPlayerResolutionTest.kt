package io.music_assistant.client.data

import io.music_assistant.client.data.MainDataSource.Companion.resolveSelectedPlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [MainDataSource.resolveSelectedPlayerId].
 *
 * The resolver is a pure function over (visible players, user choice). It
 * must never require a write-back into the persisted user setting to reach
 * the right answer — these tests pin that property down.
 */
class SelectedPlayerResolutionTest {
    @Test
    fun `persisted user choice wins when visible`() {
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("bathroom", "kitchen", "office"),
            userChoice = "kitchen",
        )
        assertEquals("kitchen", result)
    }

    @Test
    fun `falls back to first visible when user choice is not in list`() {
        // User's preferred player is offline (or removed). Don't pin them to
        // a phantom selection — give them whatever's available.
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("bathroom", "office"),
            userChoice = "kitchen",
        )
        assertEquals("bathroom", result)
    }

    @Test
    fun `falls back to first visible when no user choice`() {
        // Fresh-install case before the user has tapped a player. The
        // resolver returns this fallback on the fly; no write to the
        // persisted user choice happens — the persistence collector only
        // watches [_userSelectedPlayerId], which only [selectPlayer] writes.
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("bathroom", "kitchen"),
            userChoice = null,
        )
        assertEquals("bathroom", result)
    }

    @Test
    fun `falls back to user choice when list is empty`() {
        // Reconnecting / list still loading. Hold the persisted choice
        // rather than emit null so the UI stays stable across the gap.
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = emptyList(),
            userChoice = "kitchen",
        )
        assertEquals("kitchen", result)
    }

    @Test
    fun `returns null when nothing is set and list is empty`() {
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = emptyList(),
            userChoice = null,
        )
        assertNull(result)
    }

    @Test
    fun `tracks the visible list as it grows`() {
        // The resolver depends only on its inputs, so adding a player to
        // the visible list (e.g. local sendspin finishes registering with
        // MA after launch) flips the result without any state machine
        // having to react.
        val before = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("bathroom"),
            userChoice = null,
        )
        val after = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("sendspin-local", "bathroom"),
            userChoice = null,
        )
        assertEquals("bathroom", before)
        assertEquals("sendspin-local", after)
    }

    @Test
    fun `respects an explicit user pick even when other players are first in the list`() {
        val result = resolveSelectedPlayerId(
            visiblePlayerIds = listOf("sendspin-local", "kitchen"),
            userChoice = "kitchen",
        )
        assertEquals("kitchen", result)
    }
}
