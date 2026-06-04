package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.data.model.client.testTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemActionResolverTest {
    private fun longClick(defaultAction: ItemAction?) = resolveLongClickActions(
        item = testTrack(),
        librarySupported = false,
        canAddToPlaylist = false,
        canRemoveFromPlaylist = false,
        progressSupported = false,
        defaultAction = defaultAction,
    )

    @Test
    fun `no default keeps natural order (play now first)`() {
        assertEquals(ItemAction.Play(QueueOption.REPLACE), longClick(defaultAction = null).first())
    }

    @Test
    fun `default action is hoisted to the front exactly once`() {
        val default = ItemAction.Play(QueueOption.NEXT)
        val actions = longClick(default)
        assertEquals(default, actions.first())
        assertEquals(1, actions.count { it == default })
    }

    @Test
    fun `play-button overflow includes play now and excludes the leading default`() {
        val actions = resolvePlayButtonActions(testTrack(), default = ItemAction.Play(QueueOption.REPLACE))
        assertFalse(ItemAction.Play(QueueOption.REPLACE) in actions, "leading default must be excluded")
        assertTrue(ItemAction.Play(QueueOption.PLAY) in actions)
        assertTrue(ItemAction.StartRadio in actions, "tracks can start radio")
    }

    @Test
    fun `play-button overflow drops start radio when unavailable`() {
        val actions = resolvePlayButtonActions(testPodcastEpisode(), default = ItemAction.Play(QueueOption.REPLACE))
        assertFalse(ItemAction.StartRadio in actions)
        assertEquals(
            listOf(
                ItemAction.Play(QueueOption.PLAY),
                ItemAction.Play(QueueOption.NEXT),
                ItemAction.Play(QueueOption.ADD),
            ),
            actions,
        )
    }

    @Test
    fun `play-button overflow is empty for a non-playable item`() {
        assertEquals(emptyList(), resolvePlayButtonActions(testTrack(isPlayable = false), default = null))
    }
}
