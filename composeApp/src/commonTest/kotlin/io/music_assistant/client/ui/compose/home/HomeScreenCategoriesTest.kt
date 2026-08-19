package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.Shortcut
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the home-page row visibility rules of [getCategories]:
 * rows whose items are still loading stay visible (as placeholders), rows that
 * resolved without renderable items are hidden, duplicates collapse, and the
 * shortcuts row is appended from its own state.
 */
class HomeScreenCategoriesTest {
    private fun folder(
        itemId: String,
        provider: String = "library",
    ) = RecommendationFolder(
        itemId = itemId,
        provider = provider,
        name = itemId,
        uri = null,
        images = emptyMap(),
        items = null,
    )

    private fun loadingRow(itemId: String, provider: String = "library") =
        RecommendationRowState(folder(itemId, provider), DataState.Loading())

    private fun resolvedRow(
        itemId: String,
        items: List<AppMediaItem>,
        provider: String = "library",
    ) = RecommendationRowState(folder(itemId, provider), DataState.Data(items))

    private fun categories(
        rows: List<RecommendationRowState>,
        shortcuts: DataState<List<Shortcut>> = DataState.NoData(),
    ) = getCategories(DataState.Data(rows), shortcuts, homeRowsConfig = emptyList())

    @Test
    fun loadingRowsStayVisibleAsPlaceholders() {
        val result = categories(listOf(loadingRow("a")))

        assertEquals(listOf("a"), result.map { it.first.category.id })
        assertTrue(result.single().first.loading)
    }

    @Test
    fun rowsResolvedWithoutRenderableItemsAreHidden() {
        val result = categories(listOf(resolvedRow("empty", items = emptyList())))

        assertTrue(result.isEmpty())
    }

    @Test
    fun rowsResolvedWithPlayableItemsAreShown() {
        val result = categories(listOf(resolvedRow("a", items = listOf(testTrack()))))

        val row = result.single().first
        assertFalse(row.loading)
        assertEquals(1, row.category.items.size)
    }

    @Test
    fun rowsWithTheSameIdentityCollapseToOne() {
        val duplicated = resolvedRow("a", items = listOf(testTrack()))

        val result = categories(listOf(duplicated, duplicated))

        assertEquals(1, result.size)
    }

    @Test
    fun sameItemIdFromDifferentProvidersStaysDistinct() {
        val result = categories(
            listOf(
                loadingRow("a", provider = "library"),
                loadingRow("a", provider = "spotify"),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun shortcutsRowIsAppendedOnTopWhenUnconfigured() {
        val result = categories(
            rows = listOf(resolvedRow("a", items = listOf(testTrack()))),
            shortcuts = DataState.Data(listOf(Shortcut(testTrack()))),
        )

        assertEquals(listOf("shortcuts", "a"), result.map { it.first.category.id })
    }

    @Test
    fun nonDataRecommendationsYieldNoRows() {
        val result = getCategories(
            recommendationsState = DataState.Loading(),
            shortcutsState = DataState.Data(listOf(Shortcut(testTrack()))),
            homeRowsConfig = emptyList(),
        )

        assertTrue(result.isEmpty())
    }
}
