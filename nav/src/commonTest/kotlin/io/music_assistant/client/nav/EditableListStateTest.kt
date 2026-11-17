package io.music_assistant.client.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class EditableListStateTest {
    private val explore = listOf("discover", "search", "browse")
    private val library = listOf("artists", "albums", "tracks")
    private val system = listOf("settings")

    private fun state(onSubmit: (SidebarEdit) -> Unit = {}) = EditableListState(
        enabledIds = (explore + library + system).toSet() - "albums",
        orderedIds = explore + library + system,
        onSubmit = onSubmit,
    )

    @Test
    fun `a move stays inside its section`() {
        val state = state()

        state.move(library, fromIndex = 2, toIndex = 0)

        assertEquals(listOf("tracks", "artists", "albums"), state.ordered(library))
        assertEquals(explore, state.ordered(explore))
        assertEquals(system, state.ordered(system))
    }

    @Test
    fun `moves add up and the submit carries the order and the enabled set`() {
        var submitted: SidebarEdit? = null
        val state = state { submitted = it }
        state.toggleEditing()

        state.move(library, fromIndex = 0, toIndex = 2)
        state.move(library, fromIndex = 0, toIndex = 1)
        state.setItemEnabled("browse", false)
        state.toggleEditing()

        assertEquals(
            SidebarEdit(
                enabledIds = setOf("discover", "search", "artists", "tracks", "settings"),
                orderedIds = explore + listOf("tracks", "albums", "artists") + system,
            ),
            submitted,
        )
    }
}
