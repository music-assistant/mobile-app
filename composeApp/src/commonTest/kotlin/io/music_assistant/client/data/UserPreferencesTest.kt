package io.music_assistant.client.data

import io.music_assistant.client.data.UserPreferences.Companion.RECENT_SEARCHES
import io.music_assistant.client.data.UserPreferences.Companion.withRecentSearch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPreferencesTest {
    private fun recents(vararg terms: String) = JsonArray(terms.map(::JsonPrimitive))

    @Test
    fun `a write changes its own key on the latest server object and keeps the other keys`() = runTest {
        val client = FakePreferencesClient(buildJsonObject { put("sidebar.shortcuts", recents("a")) })
        val preferences = UserPreferences(client).apply { refresh() }
        // The web writes after this client read the object.
        client.serverPreferences = buildJsonObject {
            put("sidebar.shortcuts", recents("a", "b"))
            put("audiobook_chapter_progress", false)
            put(RECENT_SEARCHES, recents("web"))
        }

        assertTrue(preferences.addRecentSearch("jazz"))

        assertEquals(
            buildJsonObject {
                put("sidebar.shortcuts", recents("a", "b"))
                put("audiobook_chapter_progress", false)
                put(RECENT_SEARCHES, recents("jazz", "web"))
            },
            client.updates.single(),
        )
        assertEquals(listOf("jazz", "web"), preferences.recentSearches.value)
        assertFalse(preferences.isChapterProgressEnabled)
    }

    @Test
    fun `nothing is written when the preferences cannot be read`() = runTest {
        val client = FakePreferencesClient(buildJsonObject { put("theme", "dark") }).apply { failFetch = true }
        val preferences = UserPreferences(client)

        assertFalse(preferences.addRecentSearch("jazz"))

        assertTrue(client.updates.isEmpty())
        assertEquals(emptyList(), preferences.recentSearches.value)
    }

    @Test
    fun `a rejected write puts the server state back`() = runTest {
        val client = FakePreferencesClient(buildJsonObject { put(RECENT_SEARCHES, recents("rock")) })
            .apply { rejectUpdate = true }
        val preferences = UserPreferences(client)

        assertFalse(preferences.addRecentSearch("jazz"))

        assertEquals(listOf("rock"), preferences.recentSearches.value)
    }

    @Test
    fun `an unchanged value is not written`() = runTest {
        val client = FakePreferencesClient(buildJsonObject { put(RECENT_SEARCHES, recents("jazz", "rock")) })
        val preferences = UserPreferences(client)

        assertTrue(preferences.addRecentSearch(" jazz "))

        assertTrue(client.updates.isEmpty())
    }

    @Test
    fun `a write that started for the previous server does not reach the cache`() = runTest {
        val client = FakePreferencesClient()
        val preferences = UserPreferences(client)
        client.onUpdate = { preferences.clear() }

        preferences.addRecentSearch("jazz")

        assertEquals(emptyList(), preferences.recentSearches.value)
    }

    @Test
    fun `a recent search goes first trimmed without its other spellings`() {
        assertEquals(listOf("Jazz", "rock"), withRecentSearch(listOf("rock", "jazz"), "  Jazz "))
    }

    @Test
    fun `recent searches keep the five newest`() {
        assertEquals(
            listOf("f", "a", "b", "c", "d").map { it.repeat(2) },
            withRecentSearch(listOf("a", "b", "c", "d", "e").map { it.repeat(2) }, "ff"),
        )
    }

    @Test
    fun `a term shorter than two characters is not a recent search`() {
        assertNull(withRecentSearch(listOf("rock"), " j "))
    }
}
