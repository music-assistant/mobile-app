package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Covers [fetchRecommendationFolders]
 * It must bridge both shapes of the `music/recommendations` response, selected
 * by the server's schema version: rows with their items embedded, and item-less
 * rows whose contents come from a per-row `music/recommendations/items` call.
 */
class RecommendationFoldersCompatTest {
    private companion object {
        // Last schema with embedded row items, and the first without.
        const val EMBEDDED_ROWS_SCHEMA = 38
        const val ITEM_LESS_ROWS_SCHEMA = 39
    }

    private fun folderJson(itemId: String, provider: String, itemsJson: String?) = """
        {"item_id":"$itemId","provider":"$provider","name":"Row $itemId",
         "media_type":"folder","uri":null${itemsJson?.let { ""","items":$it""" } ?: ""}}
    """.trimIndent()

    private fun trackJson(itemId: String) = """
        {"item_id":"$itemId","provider":"library","name":"Track $itemId",
         "media_type":"track","is_playable":true}
    """.trimIndent()

    private class FakeClient(
        private val rowsJson: String,
        private val itemsJsonFor: (provider: String, itemId: String) -> Result<String>,
        private val rowsError: Exception? = null,
        schemaVersion: Int = ITEM_LESS_ROWS_SCHEMA,
    ) : StubServiceClient() {
        private val itemsRequestsMutex = Mutex()
        private val _itemsRequests = mutableListOf<Pair<String, String>>()
        val itemsRequests: List<Pair<String, String>>
            get() = _itemsRequests.toList()

        override val events: Flow<Event<out Any>> = emptyFlow()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> =
            when (request.command) {
                APICommands.MUSIC_RECOMMENDATIONS ->
                    rowsError?.let { Result.failure(it) } ?: Result.success(answer(rowsJson))

                APICommands.MUSIC_RECOMMENDATIONS_ITEMS -> {
                    val args = request.args ?: fail("items request missing args")
                    val provider = args["provider"]?.jsonPrimitive?.content
                        ?: fail("items request missing provider arg")
                    val itemId = args["item_id"]?.jsonPrimitive?.content
                        ?: fail("items request missing item_id arg")
                    itemsRequestsMutex.withLock {
                        _itemsRequests += provider to itemId
                    }
                    itemsJsonFor(provider, itemId).map(::answer)
                }

                else -> fail("unexpected command ${request.command}")
            }

        private fun answer(resultJson: String) = Answer(
            buildJsonObject {
                put("message_id", "test")
                put("result", myJson.parseToJsonElement(resultJson))
            },
        )
    }

    private fun repository(client: FakeClient) =
        ServiceClientMediaItemRepository(client, MediaItemFactory(client))

    @Test
    fun embeddedRowItemsAreUsedDirectly() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[${trackJson("t1")}]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { _, _ -> fail("embedded-items schema must not trigger items calls") },
            schemaVersion = EMBEDDED_ROWS_SCHEMA,
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(listOf("row1", "row2"), folders.map { it.itemId })
        assertEquals(listOf("t1"), folders[0].items?.map { it.itemId })
        assertTrue(client.itemsRequests.isEmpty())
    }

    @Test
    fun itemLessRowsGetItemsFetchedPerRow() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", null)}]
            """.trimIndent(),
            itemsJsonFor = { _, itemId -> Result.success("[${trackJson("item-of-$itemId")}]") },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(
            setOf("library" to "row1", "spotify" to "row2"),
            client.itemsRequests.toSet(),
        )
        assertEquals(listOf("item-of-row1"), folders[0].items?.map { it.itemId })
        assertEquals(listOf("item-of-row2"), folders[1].items?.map { it.itemId })
        assertTrue(folders[0].items?.single() is Track)
    }

    @Test
    fun failedItemsFetchDegradesToEmptyRow() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { provider, itemId ->
                if (provider == "spotify") {
                    Result.failure(IllegalStateException("row fetch failed"))
                } else {
                    Result.success("[${trackJson("item-of-$itemId")}]")
                }
            },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(listOf("item-of-row1"), folders[0].items?.map { it.itemId })
        assertEquals(emptyList(), folders[1].items)
    }

    @Test
    fun rowsCallFailureSurfacesAsErrorWithoutItemCalls() = runTest {
        val client = FakeClient(
            rowsJson = "unused",
            rowsError = IllegalStateException("rows call failed"),
            itemsJsonFor = { _, _ -> fail("failed rows call must not trigger items calls") },
        )
        val repo = repository(client)

        assertTrue(repo.fetchRecommendationFolders().isFailure)
        assertTrue(repo.fetchRecommendationRows().isFailure)
        assertTrue(client.itemsRequests.isEmpty())
    }
}
