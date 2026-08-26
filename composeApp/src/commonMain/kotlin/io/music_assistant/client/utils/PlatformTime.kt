package io.music_assistant.client.utils

expect fun currentTimeMillis(): Long

/**
 * Monotonic sleep-inclusive milliseconds with an arbitrary origin: only comparable to
 * other readings of this clock in this process — never to wall time, never across the
 * language bridge.
 */
expect fun monotonicMs(): Long

expect fun formatIsoDate(isoDate: String): String

/** Current device locale in a form accepted by the Music Assistant API. */
expect fun platformLocale(): String
