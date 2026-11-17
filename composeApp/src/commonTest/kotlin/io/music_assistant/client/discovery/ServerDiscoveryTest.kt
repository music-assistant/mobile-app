package io.music_assistant.client.discovery

import io.music_assistant.client.api.ConnectionInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerDiscoveryTest {
    private fun txt(vararg extra: Pair<String, String>) =
        mapOf("server_id" to "abc", "name" to "Music Assistant") + extra

    @Test
    fun baseUrlGivesHostPortAndScheme() {
        val server = MdnsService(txt("base_url" to "http://192.168.1.20:8095")).toDiscoveredServer()
        assertEquals(ConnectionInfo("192.168.1.20", 8095, isTls = false), server?.connection)
    }

    @Test
    fun httpsBaseUrlKeepsTlsDefaultPortAndBasePath() {
        val server = MdnsService(txt("base_url" to "https://ma.example.com/ma/")).toDiscoveredServer()
        assertEquals(ConnectionInfo("ma.example.com", 443, isTls = true, basePath = "/ma"), server?.connection)
    }

    @Test
    fun baseUrlWinsOverTheResolvedAddress() {
        val service = MdnsService(txt("base_url" to "http://10.0.0.5:8095"), host = "10.0.0.9", port = 1)
        assertEquals("10.0.0.5", service.toDiscoveredServer()?.connection?.host)
    }

    @Test
    fun resolvedAddressIsTheFallback() {
        val server = MdnsService(txt(), host = "10.0.0.9", port = 8095).toDiscoveredServer()
        assertEquals(ConnectionInfo("10.0.0.9", 8095, isTls = false), server?.connection)
    }

    @Test
    fun unusableRecordsGiveNoServer() {
        assertNull(MdnsService(txt("base_url" to "http://10.0.0.5:8095") - "server_id").toDiscoveredServer())
        assertNull(MdnsService(txt("base_url" to "ftp://10.0.0.5")).toDiscoveredServer())
        assertNull(MdnsService(txt("base_url" to "http://[fe80::1]:8095")).toDiscoveredServer())
        assertNull(MdnsService(txt()).toDiscoveredServer())
    }

    @Test
    fun homeAssistantAddonFlagReadsThePythonBool() {
        assertTrue(
            MdnsService(txt("base_url" to "http://h:8095", "homeassistant_addon" to "True"))
                .toDiscoveredServer()?.isHomeAssistantAddon == true,
        )
        assertEquals(
            false,
            MdnsService(txt("base_url" to "http://h:8095", "homeassistant_addon" to "False"))
                .toDiscoveredServer()?.isHomeAssistantAddon,
        )
    }

    @Test
    fun serversAreDedupedByIdAndSortedByName() = runTest {
        val browser = MdnsBrowser {
            flowOf(
                listOf(
                    MdnsService(mapOf("server_id" to "2", "name" to "beta", "base_url" to "http://b:8095")),
                    MdnsService(mapOf("server_id" to "1", "name" to "Alpha", "base_url" to "http://a:8095")),
                    MdnsService(mapOf("server_id" to "1", "name" to "Alpha", "base_url" to "http://a2:8095")),
                ),
            )
        }
        assertEquals(listOf("1", "2"), browser.musicAssistantServers().first().map { it.serverId })
    }

    @Test
    fun browserFailureGivesAnEmptyList() = runTest {
        val browser = MdnsBrowser { flow { throw IllegalStateException("mDNS blocked") } }
        assertEquals(emptyList(), browser.musicAssistantServers().first())
    }
}
