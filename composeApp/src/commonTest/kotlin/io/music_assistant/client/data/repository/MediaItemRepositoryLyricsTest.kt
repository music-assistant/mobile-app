package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.LrcLine
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** `metadata/get_track_lyrics` answers a `[lyrics, lrc_lyrics]` tuple. */
class MediaItemRepositoryLyricsTest {
    private class FakeClient(private val resultJson: String) : StubServiceClient() {
        val requests = mutableListOf<Request>()

        override val events: Flow<Event<out Any>> = emptyFlow()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            return Result.success(
                Answer(
                    buildJsonObject {
                        put("message_id", "test")
                        put("result", myJson.parseToJsonElement(resultJson))
                    },
                ),
            )
        }
    }

    private suspend fun lyricsFor(resultJson: String): Pair<Lyrics?, FakeClient> {
        val client = FakeClient(resultJson)
        val lyrics = ServiceClientMediaItemRepository(client, MediaItemFactory(client))
            .fetchTrackLyrics(AppMediaItemFixtures.track())
            .getOrThrow()
        return lyrics to client
    }

    @Test
    fun sendsTheTrackToTheLyricsCommand() = runTest {
        val (_, client) = lyricsFor("""[null, null]""")

        val request = client.requests.single()
        assertEquals(APICommands.METADATA_GET_TRACK_LYRICS, request.command)
        assertNotNull(request.args?.get("track"))
    }

    @Test
    fun syncedLyricsWinOverPlainText() = runTest {
        val (lyrics, _) = lyricsFor("""["plain", "[00:01.00]First line"]""")

        assertEquals(Lyrics.Synced(listOf(LrcLine(1000, "First line"))), lyrics)
    }

    @Test
    fun plainTextWithoutLrc() = runTest {
        val (lyrics, _) = lyricsFor("""["plain", null]""")

        assertEquals(Lyrics.Plain("plain"), lyrics)
    }

    @Test
    fun noLyricsIsNull() = runTest {
        val (lyrics, _) = lyricsFor("""[null, null]""")

        assertNull(lyrics)
    }
}
