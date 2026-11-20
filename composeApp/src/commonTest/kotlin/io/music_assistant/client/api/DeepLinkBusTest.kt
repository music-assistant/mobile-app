package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.QueueOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun playCarriesUriWithDefaults() {
        assertEquals(
            DeepLinkDestination.Play("library://radio/12"),
            parse("musicassistant://app/play?uri=library%3A%2F%2Fradio%2F12"),
        )
    }

    @Test
    fun playAppLinkCarriesPlayerAndOption() {
        assertEquals(
            DeepLinkDestination.Play("tunein://radio/s1234", "local", QueueOption.ADD),
            parse("https://www.music-assistant.io/app/play?uri=tunein://radio/s1234&player=local&option=add"),
        )
    }

    @Test
    fun playWithoutValidUriIsIgnored() {
        assertNull(parse("musicassistant://app/play"))
        assertNull(parse("musicassistant://app/play?uri=%20"))
        assertNull(parse("musicassistant://app/play?uri=12"))
    }

    @Test
    fun playWithUnknownOptionIsIgnored() {
        assertNull(parse("musicassistant://app/play?uri=library://radio/12&option=loud"))
    }

    @Test
    fun playLinkRoundTrips() {
        val uri = "library://playlist/7?x=1&y=2"
        val link = DeepLinkBus.playLink(uri)
        assertTrue(link.startsWith("https://www.music-assistant.io/app/play?uri="))
        assertEquals(DeepLinkDestination.Play(uri), parse(link))
    }

    @Test
    fun playLinkCarriesPlayer() {
        assertEquals(
            DeepLinkDestination.Play("library://radio/12", "Living Room"),
            parse(DeepLinkBus.playLink("library://radio/12", "Living Room")),
        )
        assertEquals(
            DeepLinkDestination.Play("library://radio/12", DeepLinkBus.LOCAL_PLAYER),
            parse(DeepLinkBus.playLink("library://radio/12", DeepLinkBus.LOCAL_PLAYER)),
        )
    }
}
