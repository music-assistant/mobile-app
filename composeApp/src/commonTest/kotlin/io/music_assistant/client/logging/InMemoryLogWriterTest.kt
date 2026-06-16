package io.music_assistant.client.logging

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLogWriterTest {
    // InMemoryLogWriter is a shared object, so each case uses a unique marker and asserts
    // only on its own lines — safe against entries left by other tests.

    @Test
    fun `retains Info and above`() {
        InMemoryLogWriter.log(Severity.Info, "marker-info-A1B2", "Test", null)
        InMemoryLogWriter.log(Severity.Warn, "marker-warn-A1B2", "Test", null)
        InMemoryLogWriter.log(Severity.Error, "marker-error-A1B2", "Test", null)
        val text = InMemoryLogWriter.getLogText()
        assertTrue("marker-info-A1B2" in text)
        assertTrue("marker-warn-A1B2" in text)
        assertTrue("marker-error-A1B2" in text)
    }

    @Test
    fun `drops Debug and Verbose`() {
        InMemoryLogWriter.log(Severity.Debug, "marker-debug-C3D4", "Test", null)
        InMemoryLogWriter.log(Severity.Verbose, "marker-verbose-C3D4", "Test", null)
        val text = InMemoryLogWriter.getLogText()
        assertFalse("marker-debug-C3D4" in text)
        assertFalse("marker-verbose-C3D4" in text)
    }
}
