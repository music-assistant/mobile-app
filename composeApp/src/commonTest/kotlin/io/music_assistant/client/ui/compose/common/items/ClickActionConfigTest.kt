package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.settings.DefaultClickOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClickActionConfigTest {
    private val trackSearchAddToQueue = mapOf(
        ItemKind.TRACK to mapOf(ClickContext.SEARCH to DefaultClickOption.ADD_TO_QUEUE),
    )

    @Test
    fun `null context resolves to play now`() {
        val config = ClickActionConfig(context = null, prefs = trackSearchAddToQueue)
        assertEquals(DefaultClickOption.PLAY_NOW, config.actionFor(testTrack()))
    }

    @Test
    fun `unknown kind or context resolves to play now`() {
        val config = ClickActionConfig(context = ClickContext.LIBRARY, prefs = trackSearchAddToQueue)
        // TRACK has a SEARCH entry but no LIBRARY entry.
        assertEquals(DefaultClickOption.PLAY_NOW, config.actionFor(testTrack()))
    }

    @Test
    fun `populated lookup hit is returned`() {
        val config = ClickActionConfig(context = ClickContext.SEARCH, prefs = trackSearchAddToQueue)
        assertEquals(DefaultClickOption.ADD_TO_QUEUE, config.actionFor(testTrack()))
        assertEquals(ItemAction.Play(QueueOption.ADD), config.effectiveActionFor(testTrack()))
    }

    @Test
    fun `effectiveActionFor applies the radio fallback`() {
        val config = ClickActionConfig(
            context = ClickContext.DETAIL,
            prefs = mapOf(ItemKind.PLAYLIST to mapOf(ClickContext.DETAIL to DefaultClickOption.START_RADIO)),
        )
        assertEquals(ItemAction.Play(QueueOption.REPLACE), config.effectiveActionFor(testPlaylist(isDynamic = true)))
    }

    @Test
    fun `effectiveActionFor is null for a non-playable item`() {
        val config = ClickActionConfig(context = ClickContext.SEARCH, prefs = trackSearchAddToQueue)
        assertNull(config.effectiveActionFor(testTrack(isPlayable = false)))
    }
}
