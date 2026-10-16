package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Event
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.music_assistant.client.data.model.server.events.Event as ServerEvent

/**
 * From server 2.11, item events carry no favorite, and `favorite_updated` carries the state of the
 * user. [MediaItemRepository.itemChanges] must tell the consumers which changes have the state.
 */
class MediaItemRepositoryFavoriteTest {
    private companion object {
        const val SERVER_2_10_SCHEMA = 65
        const val TRACK_URI = "library://track/1"
    }

    private fun trackJson(favorite: Boolean?) = """
        {"item_id":"1","provider":"library","name":"Track","media_type":"track",
         "uri":"$TRACK_URI","favorite":$favorite,"is_playable":true}
    """.trimIndent()

    private fun itemEvent(type: String, favorite: Boolean?) =
        """{"event":"$type","object_id":"$TRACK_URI","data":${trackJson(favorite)}}"""

    private class FakeClient(
        schemaVersion: Int,
        private val itemJson: String = "null",
    ) : StubServiceClient() {
        val requests = mutableListOf<Request>()
        private val serverEvents = MutableSharedFlow<ServerEvent<out Any>>(replay = 1)

        override val events: Flow<ServerEvent<out Any>> = serverEvents

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            return Result.success(
                Answer(
                    buildJsonObject {
                        put("message_id", "test")
                        put("result", myJson.parseToJsonElement(itemJson))
                    },
                ),
            )
        }

        /** Decodes [json] like the socket reader does, so the decoder is under test too. */
        fun emit(json: String) {
            val event = Event(myJson.parseToJsonElement(json).jsonObject).event()
            serverEvents.tryEmit(requireNotNull(event) { "Undecodable event: $json" })
        }
    }

    private fun repository(client: FakeClient) =
        ServiceClientMediaItemRepository(client, MediaItemFactory(client))

    @Test
    fun server211ItemUpdateCarriesNoFavorite() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA)
        client.emit(itemEvent("media_item_updated", favorite = null))

        val change = assertIs<MediaItemChange.Updated>(repository(client).itemChanges.first())
        assertFalse(change.carriesFavorite)
        assertEquals(true, change.favoriteAfter(current = true))
    }

    @Test
    fun server211ItemAddCarriesNoFavorite() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA)
        client.emit(itemEvent("media_item_added", favorite = null))

        val change = assertIs<MediaItemChange.Added>(repository(client).itemChanges.first())
        assertFalse(change.carriesFavorite)
    }

    @Test
    fun server210ItemUpdateCarriesTheFavorite() = runTest {
        val client = FakeClient(SERVER_2_10_SCHEMA)
        client.emit(itemEvent("media_item_updated", favorite = false))

        val change = assertIs<MediaItemChange.Updated>(repository(client).itemChanges.first())
        assertTrue(change.carriesFavorite)
        assertEquals(false, change.favoriteAfter(current = true))
    }

    @Test
    fun favoriteUpdateFetchesTheItemAndKeepsTheEventState() = runTest {
        // The fetch answers with an older state than the event.
        val client = FakeClient(SERVER_2_11_SCHEMA, itemJson = trackJson(favorite = true))
        client.emit(
            """{"event":"favorite_updated","object_id":"$TRACK_URI","data":{"uri":"$TRACK_URI",
               "media_type":"track","item_id":"1","favorite":null,"user_id":"u1"}}""",
        )

        val change = assertIs<MediaItemChange.FavoriteUpdated>(repository(client).itemChanges.first())
        assertEquals("1", change.item.itemId)
        assertNull(change.item.favorite)
        val request = client.requests.single()
        assertEquals(APICommands.MUSIC_ITEM_BY_URI, request.command)
        assertEquals(TRACK_URI, request.args?.get("uri")?.jsonPrimitive?.content)
    }
}
