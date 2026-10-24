package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.QueueOption
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the wire shape of `player_queues/play_media` against the server's api_command signature. */
class PlayMediaRequestTest {
    @Test
    fun aPlainPlaySendsNoOptionalFlag() {
        val request = Request.Library.play(
            media = listOf("library://album/42"),
            queueOrPlayerId = "queue-1",
            option = QueueOption.REPLACE_NEXT,
            endlessMixMode = false,
        )

        assertEquals("player_queues/play_media", request.command)
        assertEquals(JsonPrimitive("replace_next"), request.args?.get("option"))
        assertEquals(setOf("media", "option", "radio_mode", "queue_id"), request.args?.keys)
    }

    @Test
    fun shuffleAndStartFromBeginningAreSentOnlyWhenSet() {
        val request = Request.Library.play(
            media = listOf("library://podcast_episode/7"),
            queueOrPlayerId = "queue-1",
            option = QueueOption.PLAY,
            endlessMixMode = false,
            shuffle = true,
            startFromBeginning = true,
        )

        assertEquals(JsonPrimitive(true), request.args?.get("shuffle"))
        assertEquals(JsonPrimitive(true), request.args?.get("start_from_beginning"))
    }
}
