package io.music_assistant.client.utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExtTest {
    @Test
    fun testValidHosts() {
        val validHosts = listOf(
            // Valid IPv4 addresses
            "192.168.1.1",
            "0.0.0.0",
            "255.255.255.255",
            "127.0.0.1",
            "10.0.0.1",
            // Valid hostnames
            "localhost",
            "example.com",
            "sub.domain.co.uk",
            "my-server",
            "a123-b456"
        )
        for (host in validHosts) {
            assertTrue(host.isValidHost(), "Expected $host to be recognised as a valid host")
        }
    }

    @Test
    fun testInvalidHosts() {
        val invalidHosts = listOf(
            // Invalid IPv4 addresses
            "256.256.256.256",
            "192.168.1",
            "192.168.1.1.1",
            "123.456.78.90",
            "192.168.1.-1",
            "192.168.1.256",
            "",
            "...",
            "1.1.1.01",
            // Invalid hostnames
            "-invalidhostname",
            "invalid-.hostname",
            ".startingdot",
            "endingdot.",
            "toolonglabeltoolonglabeltoolonglabeltoolonglabeltoolonglabeltoolonglabeltoolonglabeltoolonglabel.com",
            "has space.com",
            "invalid_underscore.com"
        )
        for (host in invalidHosts) {
            assertFalse(host.isValidHost(), "Expected $host to be recognised as an invalid host")
        }
    }

    @Test
    fun testValidIpPorts() {
        val validPorts = listOf("1", "80", "65535", "12345")
        for (port in validPorts) {
            assertTrue(port.isIpPort(), "Expected $port to be recognised as a valid port")
        }
    }

    @Test
    fun testInvalidIpPorts() {
        val invalidPorts = listOf("0", "65536", "-1", "abc", "", "99999")
        for (port in invalidPorts) {
            assertFalse(port.isIpPort(), "Expected $port to be recognised as an invalid port")
        }
    }

    @Test
    fun testToMinSec() {
        assertEquals("1:05", 65.seconds.formatDuration())
        assertEquals("0:00", 0.seconds.formatDuration())
        assertEquals("--:--", (null as Duration?).formatDuration())
        assertEquals("10:00", Duration.parse("600s").formatDuration())
    }
}
