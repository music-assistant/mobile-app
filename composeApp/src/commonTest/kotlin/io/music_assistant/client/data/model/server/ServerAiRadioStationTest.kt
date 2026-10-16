package io.music_assistant.client.data.model.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun playsAsTheProvidersRadioItem() {
        val station = json.decodeFromString<ServerAiRadioStation>("""{"id": "late_night", "name": "x"}""")

        assertEquals("ai_radio://radio/late_night", station.uri)
    }

    @Test
    fun bindsAQueueDjEntry() {
        // Verbatim shape of ai_radio/queue_dj/status from schema 88.
        val status = json.decodeFromString<Map<String, ServerAiRadioQueueDj>>(
            """{"q1": {"host_id": "h1", "station_id": "late_night"}, "q2": {"host_id": "h2", "station_id": ""}}""",
        )

        assertEquals(ServerAiRadioQueueDj(hostId = "h1", stationId = "late_night"), status["q1"])
        assertEquals("", status["q2"]?.stationId)
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
