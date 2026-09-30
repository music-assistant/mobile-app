package io.music_assistant.client.imageloader

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
class RedactionDiagnosticsTest {
    private class CapturingWriter : LogWriter() {
        val lines = mutableListOf<String>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (severity >= Severity.Debug) lines += "$tag: $message ${throwable?.message.orEmpty()}"
        }
    }

    private val writer = CapturingWriter()

    @AfterTest
    fun restoreLoggers() {
        Logger.setLogWriters()
    }

    @Test
    fun artwork_diagnostics_redact_sensitive_data() = runTest {
        Logger.setMinSeverity(Severity.Debug)
        Logger.setLogWriters(writer)
        val signedUrl = "https://cdn.example.test/a.png?X-Amz-Signature=signed-secret&token=token-secret"
        val token = "token-secret"
        val payload = "payload-secret"
        val digest = artworkSha256Hex(payload.encodeToByteArray())
        val firstKey = "artwork-v1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val secondKey = "artwork-v1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60"), 1_000L)
        ArtworkDiagnostics.logProvenance("disk", signedUrl, digest)
        ArtworkDiagnostics.logProvenance("network", signedUrl, digest)
        ArtworkDiagnostics.logJoined(signedUrl)
        ArtworkDiagnostics.logProvenance("disk", firstKey)
        ArtworkDiagnostics.logProvenance("disk", secondKey)
        assertTrue(digest.isNotEmpty())

        val logs = writer.lines.joinToString("\n")
        assertTrue(writer.lines.isNotEmpty())
        assertTrue("provenance=disk" in logs)
        assertTrue("provenance=network" in logs)
        assertTrue("provenance=joined" in logs)
        assertTrue("keyId=aaaaaaaaaaaa" in logs)
        assertTrue("keyId=bbbbbbbbbbbb" in logs)
        assertNotEquals(
            writer.lines.first { "keyId=aaaaaaaaaaaa" in it },
            writer.lines.first { "keyId=bbbbbbbbbbbb" in it },
        )
        assertFalse(signedUrl in logs)
        assertFalse(token in logs)
        assertFalse(payload in logs)
        assertFalse("X-Amz-Signature" in logs)
    }
}
