package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionHistoryTest {
    private fun repo() = SettingsRepository(MapSettings(), MapSettings())

    private fun direct(host: String, serverId: String? = null) = ConnectionHistoryEntry(
        type = ConnectionType.DIRECT,
        host = host,
        port = 8095,
        isTls = false,
        serverId = serverId,
    )

    @Test
    fun anIdentifiedEntryAbsorbsTheProvisionalRowForTheSameAddress() {
        val repo = repo()

        // The connect path writes a row before `server/hello` names the server.
        repo.addOrUpdateHistoryEntry(direct("nas.local"))
        repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "server-1"))

        val entry = repo.connectionHistory.value.single()
        assertEquals("server-1", entry.serverId)
    }

    @Test
    fun twoServersOnOneAddressAreTwoRows() {
        val repo = repo()

        repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "stable"))
        repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "beta"))

        val entries = repo.connectionHistory.value
        assertEquals(2, entries.size)
        assertEquals(listOf("beta", "stable"), entries.map { it.serverId })
    }

    @Test
    fun reconnectingToTheSameServerDoesNotDuplicateItsRow() {
        val repo = repo()

        repeat(3) { repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "server-1")) }

        assertEquals(1, repo.connectionHistory.value.size)
    }

    @Test
    fun removingAnEntryLeavesTheOtherServerOnThatAddress() {
        val repo = repo()
        repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "stable"))
        repo.addOrUpdateHistoryEntry(direct("nas.local", serverId = "beta"))

        repo.removeHistoryEntry(direct("nas.local", serverId = "beta").historyKey)

        assertEquals("stable", repo.connectionHistory.value.single().serverId)
    }

    @Test
    fun credentialsAreFoundForAnAddressOnlyWhileItsTokenIsSaved() {
        val repo = repo()
        val entry = direct("nas.local", serverId = "server-1")
        repo.addOrUpdateHistoryEntry(entry)

        assertTrue(!repo.hasCredentialsForAddress(entry.serverIdentifier))

        repo.setTokenForServer("server-1", "token")
        assertTrue(repo.hasCredentialsForAddress(entry.serverIdentifier))

        repo.setTokenForServer("server-1", null)
        assertTrue(!repo.hasCredentialsForAddress(entry.serverIdentifier))
    }

    @Test
    fun aProvisionalRowOffersNoCredentials() {
        val repo = repo()
        val entry = direct("nas.local")
        repo.addOrUpdateHistoryEntry(entry)

        assertNull(repo.connectionHistory.value.single().serverId)
        assertTrue(!repo.hasCredentialsForAddress(entry.serverIdentifier))
    }
}
