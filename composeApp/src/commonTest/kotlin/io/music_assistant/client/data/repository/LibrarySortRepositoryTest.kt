package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOffer
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.SortWire
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Server 2.11 gives the sort fields of each library list through `music/sort_options`; an older
 * server, or a failed load, gets the static table.
 */
class LibrarySortRepositoryTest {
    private companion object {
        const val SERVER_2_10_SCHEMA = 65

        // The 2.11 album list, shortened: the random orders and a field unknown to the app drop out.
        const val ALBUM_OPTIONS = """[
            {"field":"sort_name","supports_direction":true,"default_direction":"asc","label_key":"sort_name"},
            {"field":"timestamp_added","supports_direction":true,"default_direction":"desc","label_key":"timestamp_added"},
            {"field":"favorite_timestamp","supports_direction":true,"default_direction":"desc","label_key":"favorite_timestamp"},
            {"field":"some_future_field","supports_direction":true,"default_direction":"asc","label_key":"some_future_field"},
            {"field":"random","supports_direction":false,"default_direction":null,"label_key":"random"}
        ]"""
    }

    private class FakeClient(schemaVersion: Int, var result: String?) : StubServiceClient() {
        val sent = mutableListOf<Request>()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            sent += request
            val json = result ?: return Result.failure(IllegalStateException("no answer"))
            return Result.success(
                Answer(
                    buildJsonObject {
                        put("message_id", "test")
                        put("result", myJson.parseToJsonElement(json))
                    },
                ),
            )
        }
    }

    @Test
    fun `server 2_10 gets the static table on order_by without a request`() = runTest {
        val client = FakeClient(SERVER_2_10_SCHEMA, ALBUM_OPTIONS)

        val menu = LibrarySortRepository(client).menu(MediaType.PODCAST)

        assertTrue(client.sent.isEmpty())
        assertEquals(SortWire.ORDER_BY, menu.wire)
        assertEquals(SortConfig.fieldsFor(MediaType.PODCAST).map(::SortOffer), menu.offers)
        assertEquals(SortConfig.defaultFor(MediaType.PODCAST), menu.default)
    }

    @Test
    fun `server 2_11 offers the known server fields`() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA, ALBUM_OPTIONS)

        val menu = LibrarySortRepository(client).menu(MediaType.ALBUM)

        val request = client.sent.single()
        assertEquals(APICommands.MUSIC_SORT_OPTIONS, request.command)
        assertEquals("library_albums", request.args?.get("listing")?.jsonPrimitive?.content)
        assertEquals(SortWire.SORT_FIELD, menu.wire)
        assertEquals(
            listOf(
                SortOffer(SortField.NAME),
                SortOffer(SortField.DATE_ADDED, defaultDescending = true),
                SortOffer(SortField.DATE_FAVORITED, defaultDescending = true),
            ),
            menu.offers,
        )
        assertEquals(SortOption(SortField.NAME), menu.default)
    }

    @Test
    fun `the app default wins while the server offers it`() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA, ALBUM_OPTIONS)

        val menu = LibrarySortRepository(client).menu(MediaType.PODCAST)

        assertEquals("library_podcasts", client.sent.single().args?.get("listing")?.jsonPrimitive?.content)
        assertEquals(SortOption(SortField.DATE_ADDED, descending = true), menu.default)
    }

    @Test
    fun `the server default applies when the server does not offer the app default`() = runTest {
        val client = FakeClient(
            SERVER_2_11_SCHEMA,
            """[
                {"field":"name","supports_direction":true,"default_direction":"asc"},
                {"field":"last_played","supports_direction":true,"default_direction":"desc"}
            ]""",
        )

        val menu = LibrarySortRepository(client).menu(MediaType.PODCAST)

        assertEquals(SortOption(SortField.EXACT_NAME), menu.default)
    }

    @Test
    fun `the options load once per list`() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA, ALBUM_OPTIONS)
        val repository = LibrarySortRepository(client)

        repository.menu(MediaType.ALBUM)
        repository.menu(MediaType.ALBUM)
        repository.menu(MediaType.TRACK)

        assertEquals(
            listOf("library_albums", "library_tracks"),
            client.sent.map { it.args?.get("listing")?.jsonPrimitive?.content },
        )
    }

    @Test
    fun `a failed load gets the static table on sort_field and asks again next time`() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA, result = null)
        val repository = LibrarySortRepository(client)

        val menu = repository.menu(MediaType.ALBUM)

        assertEquals(SortWire.SORT_FIELD, menu.wire)
        assertEquals(SortConfig.fieldsFor(MediaType.ALBUM).map(::SortOffer), menu.offers)
        client.result = ALBUM_OPTIONS
        assertEquals(SortOption(SortField.NAME), repository.menu(MediaType.ALBUM).default)
        assertEquals(2, client.sent.size)
    }

    @Test
    fun `resolve keeps an offered choice and falls back to the default otherwise`() {
        val menu = LibrarySortMenu(
            offers = listOf(SortOffer(SortField.NAME), SortOffer(SortField.YEAR, defaultDescending = true)),
            default = SortOption(SortField.NAME),
            wire = SortWire.SORT_FIELD,
        )

        assertEquals(SortOption(SortField.YEAR, descending = true), menu.resolve(SortOption(SortField.YEAR, true)))
        assertEquals(SortOption(SortField.NAME), menu.resolve(SortOption(SortField.DATE_FAVORITED)))
        assertEquals(SortOption(SortField.NAME), menu.resolve(null))
    }
}
