package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.SEARCH_LIMIT_FOCUSED
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.SEARCH_LIMIT_OVERVIEW
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.searchLimitFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchLimitTest {
    @Test
    fun `no selected filter searches all types with the overview limit`() {
        assertEquals(SEARCH_LIMIT_OVERVIEW, searchLimitFor(0))
    }

    @Test
    fun `several selected filters render as carousels and use the overview limit`() {
        assertEquals(SEARCH_LIMIT_OVERVIEW, searchLimitFor(2))
        assertEquals(SEARCH_LIMIT_OVERVIEW, searchLimitFor(3))
    }

    @Test
    fun `a single selected filter renders as a full list and fetches deeper`() {
        assertEquals(SEARCH_LIMIT_FOCUSED, searchLimitFor(1))
    }

    // Regression guard for #929: the app once asked for 200 per type, which the Spotify
    // provider turned into 20 throttled API calls (~40s) and which newer servers abandon
    // at their 8s soft timeout, dropping the provider's results entirely.
    @Test
    fun `every search limit stays inside the server provider timeout budget`() {
        val maxLimit = 40 // ceil(40 / 10) = 4 pages at ~1.0-1.5s each, under the 8s timeout
        listOf(SEARCH_LIMIT_OVERVIEW, SEARCH_LIMIT_FOCUSED).forEach { limit ->
            assertTrue(limit in 1..maxLimit, "search limit $limit exceeds the $maxLimit budget")
        }
    }
}
