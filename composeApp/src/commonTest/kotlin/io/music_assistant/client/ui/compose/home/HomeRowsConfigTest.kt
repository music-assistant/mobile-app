package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.settings.SettingsRepository.HomeRowPref
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRowsConfigTest {
    private fun folder(id: String) = RecommendationFolder(
        itemId = id,
        provider = "test",
        name = id,
        uri = null,
        images = emptyMap(),
    )

    private fun reconcile(serverIds: List<String>, config: List<HomeRowPref>) =
        reconcileHomeRows(serverIds.map { folder(it) }, config)
            .map { it.first.itemId to it.second }

    @Test
    fun `empty config keeps server order with all rows visible`() {
        assertEquals(
            listOf("a" to true, "b" to true, "c" to true),
            reconcile(listOf("a", "b", "c"), emptyList()),
        )
    }

    @Test
    fun `stored order is respected for enabled rows`() {
        val config = listOf(
            HomeRowPref("c", true),
            HomeRowPref("a", true),
            HomeRowPref("b", true),
        )
        assertEquals(
            listOf("c" to true, "a" to true, "b" to true),
            reconcile(listOf("a", "b", "c"), config),
        )
    }

    @Test
    fun `disabled rows sink to the bottom after enabled ones`() {
        val config = listOf(
            HomeRowPref("a", false),
            HomeRowPref("b", true),
            HomeRowPref("c", false),
        )
        assertEquals(
            listOf("b" to true, "a" to false, "c" to false),
            reconcile(listOf("a", "b", "c"), config),
        )
    }

    @Test
    fun `new server rows default visible at bottom of enabled section`() {
        // 'a' disabled, 'b' enabled in config; 'new' is unknown to config.
        val config = listOf(
            HomeRowPref("b", true),
            HomeRowPref("a", false),
        )
        assertEquals(
            listOf("b" to true, "new" to true, "a" to false),
            reconcile(listOf("a", "b", "new"), config),
        )
    }

    @Test
    fun `stale config ids no longer on server are ignored`() {
        val config = listOf(
            HomeRowPref("gone", true),
            HomeRowPref("a", true),
        )
        assertEquals(
            listOf("a" to true, "b" to true),
            reconcile(listOf("a", "b"), config),
        )
    }
}
