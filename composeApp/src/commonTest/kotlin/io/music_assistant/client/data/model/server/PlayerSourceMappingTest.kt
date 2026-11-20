package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.model.client.PlayerSource
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerSourceMappingTest {
    private val playerFactory = PlayerFactory(StubServiceClient())

    private fun player(json: String) = playerFactory.create(myJson.decodeFromString<ServerPlayer>(json))

    private fun kitchen(activeSource: String?, sources: String = SOURCES, syncedTo: String? = null) = player(
        """{"player_id": "kitchen", "active_source": ${activeSource.quoted()},
            "synced_to": ${syncedTo.quoted()}, "source_list": $sources}""",
    )

    private fun String?.quoted() = this?.let { "\"$it\"" } ?: "null"

    @Test
    fun `each entry of the source list maps with its flags`() {
        val sources = kitchen("kitchen").sources

        assertEquals(listOf("kitchen", SPOTIFY, "hdmi", "bluetooth"), sources.map { it.id })
        assertEquals(
            PlayerSource(id = SPOTIFY, name = "Spotify Connect", canPlayPause = true, canNextPrevious = true),
            sources[1],
        )
        assertEquals(PlayerSource(id = "hdmi", name = "TV"), sources[2], "a missing flag is false")
        assertTrue(sources[3].passive)
    }

    @Test
    fun `a player without a source list has no sources`() {
        val player = player("""{"player_id": "kitchen", "active_source": "kitchen"}""")

        assertTrue(player.sources.isEmpty())
        assertNull(player.externalSource)
        assertTrue(player.sourceChoices.isEmpty())
    }

    @Test
    fun `a queue as the active source is no external source`() {
        assertNull(kitchen("kitchen").externalSource)
        assertNull(kitchen(null).externalSource)
        assertNull(kitchen("leader", syncedTo = "leader").externalSource, "the queue of the sync leader")
    }

    @Test
    fun `a listed active source that is not the queue is the external source`() {
        assertEquals("Spotify Connect", kitchen(SPOTIFY).externalSource?.name)
        assertEquals("TV", kitchen("hdmi").externalSource?.name, "an input of the device has no uri")
    }

    @Test
    fun `a live input that the server does not list can only stop`() {
        val source = kitchen("sendspin://audio_source/phone").externalSource

        assertEquals(PlayerSource(id = "sendspin://audio_source/phone", passive = true), source)
    }

    @Test
    fun `the source menu has the selectable sources and the active one by name`() {
        assertEquals(
            listOf("Music Assistant Queue", "Spotify Connect", "TV"),
            kitchen("kitchen").sourceChoices.map { it.name },
        )
        assertEquals(
            listOf("Bluetooth", "Music Assistant Queue", "Spotify Connect", "TV"),
            kitchen("bluetooth").sourceChoices.map { it.name },
            "a passive source shows while it is active",
        )
    }

    @Test
    fun `the source menu is empty without a choice and for a sync child`() {
        assertTrue(kitchen("kitchen", sources = """[{"id": "kitchen", "name": "Queue"}]""").sourceChoices.isEmpty())
        assertTrue(kitchen("leader", syncedTo = "leader").sourceChoices.isEmpty())
    }

    @Test
    fun `the active source of the menu is the queue if the server names none`() {
        val player = kitchen(null)

        assertTrue(player.isActive(player.sources.first { it.id == "kitchen" }))
        assertFalse(player.isActive(player.sources.first { it.id == "hdmi" }))
        assertTrue(kitchen("hdmi").let { p -> p.isActive(p.sources.first { it.id == "hdmi" }) })
    }

    private companion object {
        const val SPOTIFY = "spotify://audio_source/1"
        const val SOURCES = """[
            {"id": "kitchen", "name": "Music Assistant Queue", "can_play_pause": true},
            {"id": "$SPOTIFY", "name": "Spotify Connect", "can_play_pause": true, "can_next_previous": true},
            {"id": "hdmi", "name": "TV"},
            {"id": "bluetooth", "name": "Bluetooth", "passive": true}
        ]"""
    }
}
