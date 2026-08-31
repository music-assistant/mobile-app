package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository.LibraryCategoryPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AI Radio is the only category whose visibility depends on the server. These cover the pair of
 * functions that hide it without losing the user's stored preference for it.
 */
class AiRadioCategoryVisibilityTest {
    private fun pref(category: LibraryCategory, enabled: Boolean = true) =
        LibraryCategoryPref(category.name, enabled)

    @Test
    fun `AI Radio is dropped when unavailable and kept when available`() {
        val reconciled = reconcileLibraryCategories(null)

        val hidden = visibleCategories(reconciled, aiRadioAvailable = false)
        assertFalse(hidden.any { it.first == LibraryCategory.AI_RADIO })
        assertEquals(reconciled.size - 1, hidden.size)

        assertEquals(reconciled, visibleCategories(reconciled, aiRadioAvailable = true))
    }

    @Test
    fun `no other category is affected by availability`() {
        val reconciled = reconcileLibraryCategories(null)

        val hidden = visibleCategories(reconciled, aiRadioAvailable = false).map { it.first }

        assertEquals(
            LibraryCategory.entries.filterNot { it == LibraryCategory.AI_RADIO },
            hidden,
        )
    }

    @Test
    fun `saving while AI Radio is hidden preserves its stored disabled flag`() {
        // The user turned AI Radio off, then the plugin went away.
        val stored = LibraryCategory.entries.map {
            pref(it, enabled = it != LibraryCategory.AI_RADIO)
        }
        val reconciled = reconcileLibraryCategories(stored)
        val edited = visibleCategories(reconciled, aiRadioAvailable = false)

        // The editor saves only what it could see; the hidden entry must survive.
        val merged = mergeHiddenCategories(edited, reconciled)

        assertEquals(LibraryCategory.entries.toSet(), merged.map { it.first }.toSet())
        assertTrue(merged.contains(LibraryCategory.AI_RADIO to false))
    }

    @Test
    fun `merge keeps the editor's order and appends only what it dropped`() {
        val reconciled = reconcileLibraryCategories(null)
        val edited = listOf(LibraryCategory.TRACKS to true, LibraryCategory.ALBUMS to false)

        val merged = mergeHiddenCategories(edited, reconciled)

        assertEquals(edited, merged.take(edited.size))
        assertEquals(LibraryCategory.entries.size, merged.size)
        assertEquals(LibraryCategory.entries.toSet(), merged.map { it.first }.toSet())
    }

    @Test
    fun `car tabs reconcile clamps to the car-supported universe and includes AI Radio`() {
        assertTrue(LibraryCategory.AI_RADIO in carTabCategories)

        // TRACKS is not a car tab, so a stored entry for it is dropped rather than rendered.
        val stored = listOf(pref(LibraryCategory.TRACKS), pref(LibraryCategory.ALBUMS, false))

        val result = reconcileCarTabs(stored)

        assertEquals(LibraryCategory.ALBUMS to false, result.first())
        assertEquals(carTabCategories.toSet(), result.map { it.first }.toSet())
        assertTrue(result.contains(LibraryCategory.AI_RADIO to true))
    }
}
