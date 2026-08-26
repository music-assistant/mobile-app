package io.music_assistant.client.services

import io.music_assistant.client.data.model.client.RepeatMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The media session shows 2 custom-action slots and the list has no gaps, so a
 * control that disappears drags its neighbours left. These tests pin the slot
 * rule that keeps the switch-player button from moving (issue: dynamic playlists
 * drop shuffle and repeat).
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
    fun `dynamic playlist keeps the switch player slot`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.FAVORITE),
            sessionActions(data(multiplePlayers = true, isDynamic = true)),
        )
    }

    @Test
    fun `favorite wins the free slot next to the anchor`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.FAVORITE),
            sessionActions(data(multiplePlayers = true)),
        )
    }

    @Test
    fun `shuffle takes the free slot when the item is not favoritable`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.SHUFFLE),
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
    fun `single player order is unchanged`() {
        assertEquals(
            listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE),
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
    fun `long form content keeps its seek controls`() {
        assertEquals(
            listOf(SessionAction.SEEK_BACK, SessionAction.SEEK_FORWARD),
            sessionActions(data(isLongFormContent = true, isFavoritableTrack = false)),
        )
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.SEEK_BACK),
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
    fun `no layout exceeds the two visible slots`() {
        listOf(true, false).forEach { multiplePlayers ->
            listOf(true, false).forEach { isDynamic ->
                listOf(true, false).forEach { isFavoritable ->
                    listOf(true, false).forEach { isLongForm ->
                        val actions = sessionActions(
                            data(multiplePlayers, isDynamic, isFavoritable, isLongForm),
                        )
                        assertTrue(actions.size <= 2, "too many actions: $actions")
                        assertEquals(actions.distinct(), actions, "duplicate action: $actions")
                    }
                }
            }
        }
    }

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
