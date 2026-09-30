package io.music_assistant.client.imageloader

import io.ktor.http.HeaderValue
import io.ktor.http.Headers
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.parseHeaderValue
import kotlin.math.max

internal const val ARTWORK_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

internal data class ArtworkFreshness(
    val reusable: Boolean,
    val expiresAtMs: Long,
)

internal fun artworkFreshness(
    headers: Headers,
    fetchedAtMs: Long,
): ArtworkFreshness {
    if (headers["Vary"] != null) return notReusable(fetchedAtMs)

    val lifetimeMs = parseReusableLifetime(headers) ?: return notReusable(fetchedAtMs)
    val ageMs = parseResponseAge(headers, fetchedAtMs) ?: return notReusable(fetchedAtMs)
    var expiry = fetchedAtMs + (lifetimeMs - ageMs).coerceAtLeast(0L)

    val expiresHeader = headers["Expires"]
    val expiresAtMs = expiresHeader?.let(::parseHttpDate)
    if (expiresHeader != null && expiresAtMs == null) return notReusable(fetchedAtMs)
    if (expiresAtMs != null) expiry = minOf(expiry, expiresAtMs)

    return ArtworkFreshness(expiry > fetchedAtMs, expiry)
}

private fun parseReusableLifetime(headers: Headers): Long? {
    val rawValue = headers.getAll("Cache-Control").orEmpty().joinToString(",")
    val directives = parseHeaderValue(rawValue)
    if (directives.any { it.value.isBlank() }) return null

    if (directives.any { directiveName(it) == "no-store" || directiveName(it) == "no-cache" }) {
        return null
    }

    val maxAgeDirectives = directives.filter { directiveName(it) == "max-age" }
    val maxAgeSeconds = when {
        maxAgeDirectives.isEmpty() -> null
        maxAgeDirectives.size > 1 -> return null
        else -> directiveArgument(maxAgeDirectives.single())?.toLongOrNull() ?: return null
    }
    if (maxAgeSeconds?.let { it <= 0L } == true) return null

    return maxAgeSeconds?.let(::saturatingSecondsToMs)?.coerceAtMost(ARTWORK_MAX_AGE_MS)
        ?: ARTWORK_MAX_AGE_MS
}

private fun directiveName(directive: HeaderValue): String =
    directive.value.substringBefore('=').trim().lowercase()

private fun directiveArgument(directive: HeaderValue): String? =
    if ('=' in directive.value) {
        directive.value.substringAfter('=').trim().removeSurrounding("\"")
    } else {
        directive.params.firstOrNull()?.value
    }

private fun parseResponseAge(headers: Headers, fetchedAtMs: Long): Long? {
    val dateHeader = headers["Date"]
    val dateMs = dateHeader?.let(::parseHttpDate)
    if (dateHeader != null && (dateMs == null || dateMs > fetchedAtMs)) return null

    val ageHeader = headers["Age"]
    val ageSeconds = ageHeader?.toLongOrNull()
    if (ageHeader != null && (ageSeconds == null || ageSeconds < 0L)) return null

    val apparentAgeMs = max(0L, fetchedAtMs - (dateMs ?: fetchedAtMs))
    val ageMs = ageSeconds?.let(::saturatingSecondsToMs) ?: 0L
    return max(apparentAgeMs, ageMs)
}

private fun parseHttpDate(value: String): Long? =
    try {
        value.fromHttpToGmtDate().timestamp
    } catch (_: IllegalStateException) {
        null
    }

private fun notReusable(fetchedAtMs: Long) = ArtworkFreshness(false, fetchedAtMs)

private fun saturatingSecondsToMs(seconds: Long): Long =
    if (seconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else seconds * 1000L
