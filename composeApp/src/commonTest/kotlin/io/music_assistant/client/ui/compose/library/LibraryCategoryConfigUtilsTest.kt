package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository.LibraryCategoryPref
import io.music_assistant.client.ui.compose.common.enabledFirst
import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryCategoryConfigUtilsTest {
    @Test
    fun `enabling a disabled tab moves it to the end of the enabled section`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to false,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "C", newEnabled = true)
        assertEquals(
            listOf("A" to true, "B" to true, "C" to true, "D" to false),
            result,
        )
    }

    @Test
    fun `disabling an enabled tab moves it to the top of the disabled section`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to true,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "A", newEnabled = false)
        assertEquals(
            listOf("B" to true, "C" to true, "A" to false, "D" to false),
            result,
        )
    }

    @Test
    fun `enabling an already-last-enabled tab is a no-op boundary reinsert`() {
        val items = listOf("A" to true, "B" to true)
        val result = moveToEnabledBoundary(items, "B", newEnabled = true)
        assertEquals(items, result)
    }

    @Test
    fun `disabling tab from middle preserves other ordering`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to true,
            "D" to true,
            "E" to false,
        )
        val result = moveToEnabledBoundary(items, "B", newEnabled = false)
        assertEquals(
            listOf("A" to true, "C" to true, "D" to true, "B" to false, "E" to false),
            result,
        )
    }

    @Test
    fun `enabling a tab from deep in disabled section does not disturb other disabled tabs relative order`() {
        val items = listOf(
            "A" to true,
            "B" to false,
            "C" to false,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "D", newEnabled = true)
        assertEquals(
            listOf("A" to true, "D" to true, "B" to false, "C" to false),
            result,
        )
    }

    @Test
    fun `unknown target returns input unchanged`() {
        val items = listOf("A" to true, "B" to false)
        val result = moveToEnabledBoundary(items, "Z", newEnabled = true)
        assertEquals(items, result)
    }

    @Test
    fun `disabling the only enabled tab places it first in the disabled section`() {
        val items = listOf("A" to true, "B" to false, "C" to false)
        val result = moveToEnabledBoundary(items, "A", newEnabled = false)
        assertEquals(
            listOf("A" to false, "B" to false, "C" to false),
            result,
        )
    }

    @Test
    fun `enabledFirst moves enabled items above disabled ones and keeps each section order`() {
        val items = listOf(
            "A" to true,
            "B" to false,
            "C" to true,
            "D" to false,
            "E" to true,
        )
        assertEquals(
            listOf("A" to true, "C" to true, "E" to true, "B" to false, "D" to false),
            items.enabledFirst(),
        )
    }

    @Test
    fun `a car tab added after the config was saved lands in the enabled section`() {
        // Saved before HOME existed, with one tab turned off: reconcile appends HOME after it.
        val stored = listOf(
            LibraryCategoryPref(LibraryCategory.ARTISTS.name, true),
            LibraryCategoryPref(LibraryCategory.ALBUMS.name, true),
            LibraryCategoryPref(LibraryCategory.PLAYLISTS.name, true),
            LibraryCategoryPref(LibraryCategory.PODCASTS.name, false),
            LibraryCategoryPref(LibraryCategory.RADIOS.name, true),
            LibraryCategoryPref(LibraryCategory.AUDIOBOOKS.name, true),
            LibraryCategoryPref(LibraryCategory.AI_RADIO.name, true),
        )
        val items = reconcileCarTabs(stored).enabledFirst()
        val enabledCount = items.count { it.second }
        assertEquals(
            LibraryCategory.HOME,
            items[enabledCount - 1].first,
        )
        assertEquals(listOf(LibraryCategory.PODCASTS to false), items.drop(enabledCount))
    }
}
