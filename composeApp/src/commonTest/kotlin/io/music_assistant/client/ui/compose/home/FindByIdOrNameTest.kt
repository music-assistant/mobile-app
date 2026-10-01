package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.PlayerDataFixtures.player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindByIdOrNameTest {
    private val bar = player(id = "media_player.bar", name = "Bar")
    private val kitchen = player(id = "kitchen", name = "Kitchen")

    @Test
    fun matchesIdIgnoringCase() {
        assertEquals(bar, listOf(kitchen, bar).findByIdOrName("MEDIA_PLAYER.BAR"))
    }

    @Test
    fun matchesNameIgnoringCase() {
        assertEquals(bar, listOf(kitchen, bar).findByIdOrName("bar"))
    }

    @Test
    fun idWinsOverAnotherPlayersName() {
        val namedLikeId = player(id = "x", name = "kitchen")
        assertEquals(kitchen, listOf(namedLikeId, kitchen).findByIdOrName("kitchen"))
    }

    @Test
    fun unavailablePlayerIsIgnored() {
        assertNull(listOf(bar.copy(isAvailable = false)).findByIdOrName("Bar"))
    }

    @Test
    fun playerThatNeedsSetupMatches() {
        val setup = bar.copy(isAvailable = false, needsSetup = true)
        assertEquals(setup, listOf(setup).findByIdOrName("Bar"))
    }

    @Test
    fun unknownValueMatchesNothing() {
        assertNull(listOf(bar, kitchen).findByIdOrName("Garage"))
    }
}
