package io.music_assistant.client.services

import io.music_assistant.client.data.model.client.RepeatMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [sessionActions] publishes every action [MediaNotificationData] supports -- it is not a
 * fixed-size budget that drops whatever doesn't fit. These tests pin that every applicable
 * action survives (switch-player always leads when present), and that a host filtering by
 * [MediaNotificationData.supports] alone -- never a slot count -- is what decides the list.
 *
 * A previous version of this function capped the published list at 2 "slots," which silently
 * discarded shuffle whenever a favoritable track played with more than one player configured
 * (favorite ranked ahead of it for the one leftover slot after switch-player). See git history
 * for that version if you need to compare.
 */
class MediaSessionActionsTest {
    @Test
    fun `switch player leads every multi player layout`() {
        val layouts = listOf(
            data(multiplePlayers = true),
            data(multiplePlayers = true, isDynamic = true),
            data(multiplePlayers = true, isFavoritableTrack = false),
            data(multiplePlayers = true, isDynamic = true, isFavoritableTrack = false),
            data(multiplePlayers = true, isLongFormContent = true),
        )
        layouts.forEach { layout ->
            assertEquals(
                SessionAction.SWITCH_PLAYER,
                sessionActions(layout).first(),
                "switch-player must hold the leading slot for $layout",
            )
        }
    }

    @Test
    fun `multi player layout includes every supported queue toggle, not just one`() {
        assertEquals(
            listOf(
                SessionAction.SWITCH_PLAYER,
                SessionAction.SHUFFLE,
                SessionAction.FAVORITE,
                SessionAction.REPEAT,
            ),
            sessionActions(data(multiplePlayers = true)),
        )
    }

    @Test
    fun `dynamic playlist drops shuffle and repeat but keeps switch player and favorite`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.FAVORITE),
            sessionActions(data(multiplePlayers = true, isDynamic = true)),
        )
    }

    @Test
    fun `unfavoritable multi player layout still surfaces shuffle and repeat`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.SHUFFLE, SessionAction.REPEAT),
            sessionActions(data(multiplePlayers = true, isFavoritableTrack = false)),
        )
    }

    @Test
    fun `anchor stands alone when no toggle is available`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER),
            sessionActions(
                data(multiplePlayers = true, isDynamic = true, isFavoritableTrack = false),
            ),
        )
    }

    @Test
    fun `single player layout includes every supported queue toggle`() {
        assertEquals(
            listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT),
            sessionActions(data()),
        )
        assertEquals(
            listOf(SessionAction.SHUFFLE, SessionAction.REPEAT),
            sessionActions(data(isFavoritableTrack = false)),
        )
        assertEquals(
            listOf(SessionAction.FAVORITE),
            sessionActions(data(isDynamic = true)),
        )
    }

    @Test
    fun `long form content keeps both seek controls regardless of player count`() {
        assertEquals(
            listOf(SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD),
            sessionActions(data(isLongFormContent = true, isFavoritableTrack = false)),
        )
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD),
            sessionActions(
                data(
                    multiplePlayers = true,
                    isLongFormContent = true,
                    isFavoritableTrack = false,
                ),
            ),
        )
    }

    @Test
    fun `every layout is exactly its supported actions, with no duplicates`() {
        val bools = listOf(true, false)
        val layouts = bools.flatMap { multiplePlayers ->
            bools.flatMap { isDynamic ->
                bools.flatMap { isFavoritable ->
                    bools.map { isLongForm ->
                        Quad(multiplePlayers, isDynamic, isFavoritable, isLongForm)
                    }
                }
            }
        }

        layouts.forEach { combo ->
            val layout = data(
                combo.multiplePlayers,
                combo.isDynamic,
                combo.isFavoritable,
                combo.isLongForm,
            )
            val actions = sessionActions(layout)
            assertEquals(actions.distinct(), actions, "duplicate action: $actions")

            val expectedCount = when {
                combo.isLongForm -> (if (combo.multiplePlayers) 1 else 0) + 2
                else -> (if (combo.multiplePlayers) 1 else 0) +
                    listOf(!combo.isDynamic, combo.isFavoritable, !combo.isDynamic).count { it }
            }
            assertTrue(
                actions.size == expectedCount,
                "expected $expectedCount actions for $layout, got $actions",
            )
        }
    }

    private data class Quad(
        val multiplePlayers: Boolean,
        val isDynamic: Boolean,
        val isFavoritable: Boolean,
        val isLongForm: Boolean,
    )

    /**
     * Mirrors the gates in [MediaNotificationData.from]: a dynamic playlist nulls both
     * queue toggles because the server does not accept them there.
     */
    private fun data(
        multiplePlayers: Boolean = false,
        isDynamic: Boolean = false,
        isFavoritableTrack: Boolean = true,
        isLongFormContent: Boolean = false,
    ) = MediaNotificationData(
        multiplePlayers = multiplePlayers,
        longItemId = null,
        name = null,
        artist = null,
        album = null,
        repeatMode = RepeatMode.OFF.takeIf { !isDynamic },
        shuffleEnabled = false.takeIf { !isDynamic },
        isLongFormContent = isLongFormContent,
        isFavoritableTrack = isFavoritableTrack,
        isFavorite = false,
        isPlaying = true,
        imageUrl = null,
        chapterName = null,
        elapsedTime = null,
        elapsedUpdateTimeMs = null,
        playerName = null,
        duration = null,
    )
}
