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
 * Multi-player order is deliberately different from single-player order (favorite before
 * shuffle vs. shuffle before favorite): the phone's own notification surfaces render only the
 * first two published actions with no overflow, so that order is what a real multi-player phone
 * user sees, while Android Auto (which always reports multiplePlayers=false, so it only ever
 * sees the single-player order) gets the full list via its own overflow menu regardless.
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
                SessionAction.FAVORITE,
                SessionAction.SHUFFLE,
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
    fun `every layout publishes exactly its supported actions, with no duplicates`() {
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
            assertTrue(
                actions.all { layout.supports(it) },
                "published an unsupported action for $layout: $actions",
            )

            val queueToggles =
                listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT)
            val expectedToggles = if (combo.isLongForm) {
                emptySet()
            } else {
                queueToggles.filter { layout.supports(it) }.toSet()
            }
            assertEquals(
                expectedToggles,
                actions.filter { it in queueToggles }.toSet(),
                "missing a supported queue toggle for $layout: $actions",
            )
        }
    }

    @Test
    fun `stream favorite wins the same slot as track favorite`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.FAVORITE, SessionAction.SHUFFLE, SessionAction.REPEAT),
            sessionActions(
                data(
                    multiplePlayers = true,
                    isFavoritableTrack = false,
                    isFavoritableStream = true,
                ),
            ),
        )
        assertEquals(
            listOf(SessionAction.SHUFFLE, SessionAction.FAVORITE, SessionAction.REPEAT),
            sessionActions(data(isFavoritableTrack = false, isFavoritableStream = true)),
        )
    }

    @Test
    fun `no favorite slot when neither track nor stream is favoritable`() {
        assertEquals(
            listOf(SessionAction.SWITCH_PLAYER, SessionAction.SHUFFLE, SessionAction.REPEAT),
            sessionActions(
                data(
                    multiplePlayers = true,
                    isFavoritableTrack = false,
                    isFavoritableStream = false,
                ),
            ),
        )
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
        isFavoritableStream: Boolean = false,
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
        isFavoritableStream = isFavoritableStream,
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
