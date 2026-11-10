package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.settings.SettingsRepository.LibraryCategoryPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Party exists only while the `party` plugin is loaded, and the user can hide it like Browse. */
class PartyCategoryVisibilityTest {
    @Test
    fun `Party is dropped when unavailable and kept when available`() {
        val reconciled = reconcileLibraryCategories(null)

        val hidden = visibleCategories(reconciled, aiRadioAvailable = true, partyAvailable = false)
        assertEquals(reconciled.filterNot { it.first == LibraryCategory.PARTY }, hidden)

        assertEquals(reconciled, visibleCategories(reconciled, aiRadioAvailable = true, partyAvailable = true))
    }

    @Test
    fun `the car tabs never hold Party`() {
        assertFalse(LibraryCategory.PARTY in carTabCategories)
    }

    @Test
    fun `a config saved before Party gets it enabled`() {
        val savedBefore = (libraryCategories - LibraryCategory.PARTY).map { LibraryCategoryPref(it.name, true) }

        assertTrue(reconcileLibraryCategories(savedBefore).contains(LibraryCategory.PARTY to true))
    }

    @Test
    fun `hiding Party in the editor stores it disabled`() {
        val reconciled = reconcileLibraryCategories(null)
        val visible = visibleCategories(reconciled, aiRadioAvailable = true, partyAvailable = true).map { it.first }
        val enabled = (visible - LibraryCategory.PARTY).map { it.name }

        val saved = applySidebarEdit(reconciled, visible.toSet(), enabled, visible.map { it.name })

        assertTrue(saved.contains(LibraryCategory.PARTY to false))
        assertTrue(saved.filterNot { it.first == LibraryCategory.PARTY }.all { it.second })
    }

    @Test
    fun `saving while the plugin is gone keeps the stored Party flag`() {
        val stored = libraryCategories.map { LibraryCategoryPref(it.name, enabled = it != LibraryCategory.PARTY) }
        val reconciled = reconcileLibraryCategories(stored)
        val visible = visibleCategories(reconciled, aiRadioAvailable = true, partyAvailable = false).map { it.first }

        val saved = applySidebarEdit(reconciled, visible.toSet(), visible.map { it.name }, visible.map { it.name })

        assertEquals(reconciled, saved)
    }
}
