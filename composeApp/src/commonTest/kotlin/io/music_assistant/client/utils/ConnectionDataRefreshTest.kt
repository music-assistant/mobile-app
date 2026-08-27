package io.music_assistant.client.utils

import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards [withRefreshedServerInfo], the merge rule behind the live `core_state_updated` refresh.
 *
 * The refresh runs on a live session, so it must stay strictly additive: it may replace the
 * cached [ServerInfo] of the connected server and nothing else. Filling an empty cache would
 * defeat the `AwaitingServerInfo` gate WebRTC relies on after a reconnect.
 */
class ConnectionDataRefreshTest {
    private fun serverInfo(id: String, name: String? = null, schema: Int? = 59) =
        ServerInfo(serverId = id, name = name, schemaVersion = schema)

    private fun connected(cached: ServerInfo?) = ConnectionData(
        serverInfo = cached,
        user = User(userId = "u1", username = "user", displayName = "User"),
        token = "tok",
    )

    @Test
    fun refreshesCachedServerInfoForTheSameServer() {
        val data = connected(serverInfo("srv1", name = "Old"))

        val refreshed = data.withRefreshedServerInfo(serverInfo("srv1", name = "New"))

        assertEquals("New", refreshed.serverInfo?.name)
    }

    @Test
    fun keepsAuthFieldsUntouched() {
        val data = connected(serverInfo("srv1", name = "Old"))

        val refreshed = data.withRefreshedServerInfo(serverInfo("srv1", name = "New"))

        assertEquals(data.user, refreshed.user)
        assertEquals(data.token, refreshed.token)
        assertEquals(data.authProcessState, refreshed.authProcessState)
        assertEquals(data.wasAutoLogin, refreshed.wasAutoLogin)
        assertEquals(data.needsServerReauth, refreshed.needsServerReauth)
        assertTrue(refreshed.dataConnectionState is DataConnectionState.Authenticated)
    }

    @Test
    fun ignoresPayloadFromAnotherServer() {
        val data = connected(serverInfo("srv1", name = "Old"))

        val refreshed = data.withRefreshedServerInfo(serverInfo("srv2", name = "Intruder"))

        assertSame(data, refreshed)
    }

    @Test
    fun neverFillsAnEmptyCache() {
        // WebRTC clears serverInfo on reconnect to hold `authorize` until the fresh
        // `server/hello` arrives. An event must not short-circuit that gate.
        val data = connected(cached = null)

        val refreshed = data.withRefreshedServerInfo(serverInfo("srv1"))

        assertSame(data, refreshed)
        assertSame(DataConnectionState.AwaitingServerInfo, refreshed.dataConnectionState)
    }
}
