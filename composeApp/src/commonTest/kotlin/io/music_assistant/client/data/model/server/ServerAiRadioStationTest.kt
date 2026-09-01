package io.music_assistant.client.data.model.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two source-playlist fields are the only route to artwork on a station row, and they are
 * defaulted so one odd element cannot fail the decode of the whole `stations/list` answer.
 */
class ServerAiRadioStationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bindsSourcePlaylistFromAFullServerPayload() {
        // Verbatim shape of one element of ai_radio/stations/list.
        val payload = """
            {
              "id": "late_night",
              "name": "Late Night",
              "source_playlist_id": "playlist-42",
              "source_playlist_provider": "spotify--abc",
              "default_player_id": "player-1",
              "max_duration_minutes": 0.0,
              "shuffle_source_tracks": true,
              "host_id": "default_host"
            }
        """.trimIndent()

        val station = json.decodeFromString<ServerAiRadioStation>(payload)

        assertEquals("late_night", station.id)
        assertEquals("Late Night", station.name)
        assertEquals("playlist-42", station.sourcePlaylistId)
        assertEquals("spotify--abc", station.sourcePlaylistProvider)
    }

    /**
     * The queue a run owns is the only safe thing to clear when it is stopped. If this binding
     * silently breaks, the clear becomes a no-op — or worse, tempts a fallback to the currently
     * selected player, which may be playing something else entirely.
     */
    @Test
    fun bindsTheQueueTheRunOwns() {
        val payload = """
            {
              "session_id": "abc123",
              "station_id": "late_night",
              "queue_id": "player-leader-1",
              "status": "running",
              "error": null
            }
        """.trimIndent()

        val session = json.decodeFromString<ServerAiRadioSession>(payload)

        assertEquals("player-leader-1", session.queueId)
        assertTrue(session.isRunning)
    }

    @Test
    fun aRunWithoutAQueueDecodesToNull() {
        // The plugin claims a queue partway through a run, so an early session has none yet.
        val session = json.decodeFromString<ServerAiRadioSession>(
            """{"session_id": "abc123", "station_id": "s1", "status": "running"}""",
        )

        assertNull(session.queueId)
    }

    @Test
    fun decodesWithoutTheSourcePlaylistFields() {
        val station = json.decodeFromString<ServerAiRadioStation>(
            """{"id": "s1", "name": "S1"}""",
        )

        // No id means no lookup is attempted; the row falls back to its placeholder.
        assertEquals("", station.sourcePlaylistId)
        assertEquals("library", station.sourcePlaylistProvider)
    }
}
