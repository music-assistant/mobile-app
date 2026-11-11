package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.M3U_MIME_TYPE
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.server.PlaylistMatchPolicy
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.RecordingFileSharer
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistTransferTest {
    private val sharer = RecordingFileSharer()

    @Test
    fun exportSharesTheM3uTextAsAFileNamedAfterThePlaylist() = runTest {
        val client = RecordingClient(schemaVersion = SERVER_2_11_SCHEMA, result = JsonPrimitive("#EXTM3U\nx"))

        val result = PlaylistTransfer(client, sharer).export(testPlaylist(), "Export")

        assertTrue(result.isSuccess)
        assertEquals(APICommands.MUSIC_PLAYLISTS_EXPORT_PLAYLIST, client.requests.single().command)
        assertEquals("id", client.requests.single().args?.get("db_playlist_id")?.jsonPrimitive?.content)
        assertEquals("#EXTM3U\nx", sharer.files["cache/name.m3u8"])
        assertEquals(listOf("cache/name.m3u8" to M3U_MIME_TYPE), sharer.shared)
    }

    @Test
    fun anEmptyExportSharesNothing() = runTest {
        val client = RecordingClient(schemaVersion = SERVER_2_11_SCHEMA, result = JsonNull)

        val result = PlaylistTransfer(client, sharer).export(testPlaylist(), "Export")

        assertTrue(result.isFailure)
        assertTrue(sharer.shared.isEmpty())
    }

    @Test
    fun importSendsThePolicyAndTheSourcesToAServer211() = runTest {
        val client = RecordingClient(schemaVersion = SERVER_2_11_SCHEMA, result = playlistResult("Road trip"))

        val name = PlaylistTransfer(client, sharer)
            .import("#EXTM3U", listOf("spotify--1"), PlaylistMatchPolicy.EXACT)
            .getOrNull()

        val args = client.requests.single().args
        assertEquals("Road trip", name)
        assertEquals(APICommands.MUSIC_PLAYLISTS_IMPORT_PLAYLIST, client.requests.single().command)
        assertEquals("true", args?.get("library_matching")?.jsonPrimitive?.content)
        assertEquals("exact", args?.get("match_policy")?.jsonPrimitive?.content)
        assertEquals(listOf("spotify--1"), args?.get("match_providers")?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun importLeavesThePolicyOutForAnOlderServer() = runTest {
        val client = RecordingClient(schemaVersion = SERVER_2_11_SCHEMA - 1, result = playlistResult("Mix"))

        PlaylistTransfer(client, sharer).import("#EXTM3U", null, PlaylistMatchPolicy.EXACT)

        val args = client.requests.single().args
        assertNull(args?.get("match_policy"))
        assertNull(args?.get("match_providers"))
    }

    private fun playlistResult(name: String) = buildJsonObject {
        put("item_id", "7")
        put("provider", "library")
        put("name", name)
        put("media_type", "playlist")
    }

    private class RecordingClient(schemaVersion: Int, private val result: JsonElement) : StubServiceClient() {
        val requests = mutableListOf<Request>()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "ma.local", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "server-1", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            return Result.success(Answer(buildJsonObject { put("result", result) }))
        }
    }
}
