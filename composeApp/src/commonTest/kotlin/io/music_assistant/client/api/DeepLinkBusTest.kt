package io.music_assistant.client.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkBusTest {
    private fun parse(url: String) = DeepLinkBus().apply { handle(url) }.pending.value

    @Test
    fun playersWithoutParameterSelectsNothing() {
        assertEquals(DeepLinkDestination.Players(), parse("musicassistant://app/players"))
    }

    @Test
    fun customSchemeCarriesPlayer() {
        assertEquals(
            DeepLinkDestination.Players("Bar"),
            parse("musicassistant://app/players?player=Bar"),
        )
    }

    @Test
    fun appLinkCarriesPlayer() {
        assertEquals(
            DeepLinkDestination.Players("media_player.bar"),
            parse("https://music-assistant.io/app/players?player=media_player.bar"),
        )
    }

    @Test
    fun encodedNameIsDecoded() {
        assertEquals(
            DeepLinkDestination.Players("Living Room"),
            parse("musicassistant://app/players?player=Living%20Room"),
        )
    }

    @Test
    fun blankPlayerMeansNoSelection() {
        assertEquals(DeepLinkDestination.Players(), parse("musicassistant://app/players?player=%20"))
        assertEquals(DeepLinkDestination.Players(), parse("musicassistant://app/players?player="))
    }

    @Test
    fun consumeClearsPending() {
        val bus = DeepLinkBus().apply { handle("musicassistant://app/players?player=Bar") }
        bus.consume(DeepLinkDestination.Players("Bar"))
        assertNull(bus.pending.value)
    }
}
