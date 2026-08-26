package io.music_assistant.client.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionInfoTest {
    @Test
    fun `builds root urls when base path is empty`() {
        val info = ConnectionInfo(host = "nas.local", port = 8095, isTls = false)
        assertEquals("http://nas.local:8095", info.webUrl)
        assertEquals("ws://nas.local:8095", info.wsUrl)
    }

    @Test
    fun `builds tls urls`() {
        val info = ConnectionInfo(host = "nas.local", port = 8095, isTls = true)
        assertEquals("https://nas.local:8095", info.webUrl)
        assertEquals("wss://nas.local:8095", info.wsUrl)
    }

    @Test
    fun `appends base path without a trailing slash`() {
        val info = ConnectionInfo(host = "ha.example.org", port = 443, isTls = true, basePath = "/ma")
        assertEquals("https://ha.example.org:443/ma", info.webUrl)
        assertEquals("wss://ha.example.org:443/ma", info.wsUrl)
    }

    @Test
    fun `normalizes a stale persisted base path`() {
        val info = ConnectionInfo(host = "h", port = 1, isTls = false, basePath = "ma/")
        assertEquals("http://h:1/ma", info.webUrl)
    }

    @Test
    fun `normalizeBasePath produces the canonical form`() {
        assertEquals("", ConnectionInfo.normalizeBasePath(""))
        assertEquals("", ConnectionInfo.normalizeBasePath("  "))
        assertEquals("", ConnectionInfo.normalizeBasePath("/"))
        assertEquals("/ma", ConnectionInfo.normalizeBasePath("ma"))
        assertEquals("/ma", ConnectionInfo.normalizeBasePath("/ma"))
        assertEquals("/ma", ConnectionInfo.normalizeBasePath("/ma/"))
        assertEquals("/ma", ConnectionInfo.normalizeBasePath(" /ma/ "))
        assertEquals("/a/b", ConnectionInfo.normalizeBasePath("/a/b/"))
    }

    @Test
    fun `previewWsUrl tolerates half-typed input`() {
        assertEquals("ws://:", ConnectionInfo.previewWsUrl("", "", isTls = false, basePath = ""))
        assertEquals(
            "wss://ha.example.org:443/ma",
            ConnectionInfo.previewWsUrl("ha.example.org", "443", isTls = true, basePath = "ma/"),
        )
    }
}
