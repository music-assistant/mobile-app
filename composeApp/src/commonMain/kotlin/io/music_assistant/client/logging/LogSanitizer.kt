package io.music_assistant.client.logging

/**
 * Redacts sensitive data from log text before sharing.
 * Applied at the sharing boundary as a safety net — individual log statements
 * should also avoid logging sensitive data in the first place.
 */
object LogSanitizer {
    private data class RedactionRule(val pattern: Regex, val replacement: String)

    private val rules = listOf(
        // URLs with common schemes (http, https, ws, wss)
        RedactionRule(Regex("""(https?|wss?)://\S+"""), "[REDACTED_URL]"),
        // IPv4 addresses (with optional port)
        RedactionRule(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d{1,5})?\b"""), "[REDACTED_IP]"),
        // IPv6 addresses (4+ colon-separated hex groups)
        RedactionRule(Regex("""\b[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){3,7}\b"""), "[REDACTED_IP]"),
        // Remote IDs (26-char base32 uppercase alphanumeric, derived from DTLS fingerprint)
        RedactionRule(Regex("""\b[A-Z0-9]{26}\b"""), "[REDACTED_REMOTE_ID]"),
        // UUIDs (session IDs, etc.)
        RedactionRule(
            Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""),
            "[REDACTED_ID]",
        ),
        // Possessive local part (`++`): the class excludes '@', so backtracking
        // it can never match — skips rescanning every '@'-less word run.
        RedactionRule(Regex("""\b[\w.+-]++@[\w.-]+\.\w{2,}\b"""), "[REDACTED_EMAIL]"),
    )

    fun sanitize(text: String): String =
        rules.fold(text) { result, rule -> rule.pattern.replace(result, rule.replacement) }
}
