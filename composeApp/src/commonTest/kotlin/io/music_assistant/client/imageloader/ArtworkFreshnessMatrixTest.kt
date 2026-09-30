package io.music_assistant.client.imageloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkFreshnessMatrixTest {
    @Test
    fun freshness_policy_matrix() {
        val now = 1_600_000_000_000L
        assertTrue(freshness(now, "max-age=60").reusable)
        assertEquals(now + 60_000L, freshness(now, "max-age=60").expiresAtMs)

        assertFalse(freshness(now, "max-age=0").reusable)
        assertFalse(freshness(now, "max-age=-1").reusable)
        assertFalse(freshness(now, "max-age=not-a-number").reusable)
        assertFalse(freshness(now, "max-age=999999999999999999999").reusable)
        assertFalse(freshness(now, "max-age").reusable)
        assertFalse(freshness(now, "max-age=").reusable)
        assertFalse(freshness(now, "max-age=60, max-age=60").reusable)
        assertFalse(freshness(now, "max-age=60, max-age=120").reusable)
        assertEquals(now + 60_000L, freshness(now, "max-age=\"60\"").expiresAtMs)

        assertFalse(freshness(now, "no-store").reusable)
        assertFalse(freshness(now, "no-cache").reusable)
        assertFalse(artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60", "Vary" to "Accept"), now).reusable)
        assertFalse(artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60", "Vary" to "*"), now).reusable)

        assertFalse(artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60", "Age" to "-1"), now).reusable)
        assertFalse(
            artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60", "Age" to "not-a-number"), now).reusable,
        )
        assertFalse(
            artworkFreshness(
                ktorHeaders("Cache-Control" to "max-age=60", "Age" to "999999999999999999999"),
                now,
            ).reusable,
        )

        assertFalse(
            artworkFreshness(ktorHeaders("Cache-Control" to "max-age=60", "Date" to "not-a-date"), now).reusable,
        )
        assertFalse(
            artworkFreshness(
                ktorHeaders("Cache-Control" to "max-age=60", "Date" to "Thu, 01 Jan 2099 00:00:00 GMT"),
                now,
            ).reusable,
        )
        assertFalse(
            artworkFreshness(
                ktorHeaders("Cache-Control" to "max-age=60", "Date" to "Thu, 01 Jan 1970 00:00:00 GMT"),
                now,
            ).reusable,
        )

        assertFalse(
            artworkFreshness(
                ktorHeaders(
                    "Cache-Control" to "max-age=3600",
                    "Expires" to "Thu, 01 Jan 1970 00:00:00 GMT",
                ),
                now,
            ).reusable,
        )
        assertFalse(
            artworkFreshness(
                ktorHeaders(
                    "Cache-Control" to "max-age=3600",
                    "Expires" to "not-a-date",
                ),
                now,
            ).reusable,
        )

        val fallback = artworkFreshness(ktorHeaders(), now)
        assertTrue(fallback.reusable)
        assertEquals(now + ARTWORK_MAX_AGE_MS, fallback.expiresAtMs)
        assertTrue(
            artworkFreshness(
                ktorHeaders("Cache-Control" to "max-age=999999999999"),
                now,
            ).expiresAtMs <= now + ARTWORK_MAX_AGE_MS,
        )
    }

    private fun freshness(now: Long, cacheControl: String) = artworkFreshness(
        ktorHeaders("Cache-Control" to cacheControl),
        now,
    )
}
