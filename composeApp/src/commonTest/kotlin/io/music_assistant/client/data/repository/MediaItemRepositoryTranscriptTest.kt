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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `music/podcasts/podcast_episode_transcript` answers a `[text, cues]` tuple. */
class MediaItemRepositoryTranscriptTest {
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

    private val episode = AppMediaItemFixtures.episode(itemId = "ep-7")

    private suspend fun transcriptFor(resultJson: String): Pair<Lyrics?, FakeClient> {
        val client = FakeClient(resultJson)
        val transcript = ServiceClientMediaItemRepository(client, MediaItemFactory(client))
            .fetchEpisodeTranscript(episode)
            .getOrThrow()
        return transcript to client
    }

    @Test
    fun sendsTheEpisodeIdAndProviderToTheTranscriptCommand() = runTest {
        val (_, client) = transcriptFor("""[null, null]""")

        val request = client.requests.single()
        assertEquals(APICommands.MUSIC_PODCASTS_PODCAST_EPISODE_TRANSCRIPT, request.command)
        assertEquals("ep-7", request.args?.get("item_id")?.jsonPrimitive?.content)
        assertEquals(episode.provider, request.args?.get("provider_instance_id_or_domain")?.jsonPrimitive?.content)
    }

    @Test
    fun timedCuesBecomeSyncedLinesInTimeOrderWithoutBlankLines() = runTest {
        val (transcript, _) = transcriptFor(
            """["Hello. World.", [
                {"start": 2.5, "end": 4.0, "text": " World. ", "speaker": "Host"},
                {"start": 0.25, "text": "Hello."},
                {"start": 1.0, "text": "   "}
            ]]""",
        )

        assertEquals(Lyrics.Synced(listOf(LrcLine(250, "Hello."), LrcLine(2500, "World."))), transcript)
    }

    @Test
    fun textWithoutCuesIsPlain() = runTest {
        val (transcript, _) = transcriptFor("""["Hello. World.", []]""")

        assertEquals(Lyrics.Plain("Hello. World."), transcript)
    }

    @Test
    fun noTranscriptIsNull() = runTest {
        assertNull(transcriptFor("""[null, null]""").first)
        assertNull(transcriptFor("""null""").first)
    }
}
