package io.music_assistant.client.imageloader

import io.ktor.http.Headers
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkCachePolicyTest {
    @Test
    fun freshnessPolicyMatrix() {
        val now = 1_000_000L
        assertTrue(artworkFreshness(headers("Cache-Control" to "max-age=60"), now).reusable)
        assertFalse(artworkFreshness(headers("Cache-Control" to "no-store"), now).reusable)
        assertFalse(artworkFreshness(headers("Cache-Control" to "no-cache"), now).reusable)
        assertFalse(artworkFreshness(headers("Cache-Control" to "max-age=0"), now).reusable)
        assertFalse(
            artworkFreshness(
                Headers.build {
                    append("Cache-Control", "max-age=3600")
                    append("Cache-Control", "no-store")
                },
                now,
            ).reusable,
        )
        assertFalse(artworkFreshness(headers("Vary" to "*"), now).reusable)
        assertTrue(artworkFreshness(Headers.Empty, now).reusable)
    }

    @Test
    fun sevenDayCapIsApplied() {
        val result = artworkFreshness(headers("Cache-Control" to "max-age=999999999"), 0L)
        assertTrue(result.expiresAtMs <= ARTWORK_MAX_AGE_MS)
    }

    private fun headers(vararg values: Pair<String, String>): Headers = Headers.build {
        values.forEach { append(it.first, it.second) }
    }
}
