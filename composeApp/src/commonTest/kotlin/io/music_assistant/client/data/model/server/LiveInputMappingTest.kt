package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveInputMappingTest {
    private val playerFactory = PlayerFactory(StubServiceClient())

    private fun player(json: String) = playerFactory.create(myJson.decodeFromString<ServerPlayer>(json))

    @Test
    fun `an active source that is a uri is a live input`() {
        val player = player("""{"player_id": "kitchen", "active_source": "sendspin://audio_source/phone"}""")

        assertEquals("sendspin://audio_source/phone", player.liveInputUri)
    }

    @Test
    fun `a queue as the active source is no live input`() {
        assertNull(player("""{"player_id": "kitchen", "active_source": "kitchen"}""").liveInputUri)
        assertNull(player("""{"player_id": "kitchen", "active_source": "leader"}""").liveInputUri)
        assertNull(player("""{"player_id": "kitchen"}""").liveInputUri)
    }

    @Test
    fun `a live input can pause only if its source entry says so`() {
        fun onInput(sources: String) = player(
            """{"player_id": "kitchen", "active_source": "spotify://audio_source/1", "source_list": $sources}""",
        )

        assertTrue(onInput("""[{"id": "spotify://audio_source/1", "can_play_pause": true}]""").canPauseLiveInput)
        assertFalse(onInput("""[{"id": "spotify://audio_source/1", "can_play_pause": false}]""").canPauseLiveInput)
        assertFalse(onInput("""[{"id": "spotify://audio_source/1"}]""").canPauseLiveInput)
        assertFalse(onInput("""[{"id": "kitchen", "can_play_pause": true}]""").canPauseLiveInput, "not listed")
        assertFalse(onInput("[]").canPauseLiveInput)
    }

    @Test
    fun `the queue entry of the source list is no live input`() {
        val onQueue = player(
            """{"player_id": "kitchen", "active_source": "kitchen",
                "source_list": [{"id": "kitchen", "can_play_pause": true}]}""",
        )

        assertNull(onQueue.liveInputUri)
        assertFalse(onQueue.canPauseLiveInput)
    }
}
