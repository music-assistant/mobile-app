package io.music_assistant.client.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSanitizerTest {
    @Test
    fun `redacts a simple email address`() {
        val secret = "user@example.com"
        val out = LogSanitizer.sanitize("contact $secret for details")
        assertFalse(out.contains(secret), "email leaked: $out")
        assertTrue(out.contains("REDACTED_EMAIL"), "no redaction marker: $out")
    }

    @Test
    fun `redacts an email with a complex local part`() {
        // Exercises the possessive local-part quantifier.
        val secret = "first.last+tag-x@sub.example.co.uk"
        val out = LogSanitizer.sanitize("from=$secret done")
        assertFalse(out.contains(secret), "email leaked: $out")
        assertFalse(out.contains("@"), "address fragment leaked: $out")
    }

    @Test
    fun `leaves text without sensitive data unchanged`() {
        val line = "just a normal log line with several plain words"
        assertEquals(line, LogSanitizer.sanitize(line))
    }
}
