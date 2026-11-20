package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
