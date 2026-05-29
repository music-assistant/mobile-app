package io.music_assistant.client.ui.compose.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

/**
 * Allows multiple back stacks to be treated as one for use with components like
 * [androidx.navigation3.ui.NavDisplay] (with [toEntries] when combined with a
 * [androidx.compose.material3.NavigationBar].
 *
 * The back stack entries are combined to provide "exit through home" style navigation where
 * navigating backways from the last element of any back stack except the first leads to the top
 * element of the first back stack.
 */
class MultiBackStack<T : NavKey>(private val backStacks: List<MutableList<T>>) {
    var currentBackStack by mutableStateOf(0)

    private val roots = backStacks.map { it.first() }

    /**
     * Clear the current back stack and reset it back to its original state
     */
    fun resetCurrentBackStack() {
        backStacks[currentBackStack].apply {
            clear()
            add(roots[currentBackStack])
        }
    }

    fun add(element: T) {
        backStacks[currentBackStack].add(element)
    }

    fun removeLastOrNull(): NavKey? {
        return if (currentBackStack != 0 && backStacks[currentBackStack].size == 1) {
            currentBackStack = 0
            backStacks[currentBackStack].lastOrNull()
        } else {
            backStacks[currentBackStack].removeLastOrNull()
        }
    }

    fun toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val activeBackStacks = if (currentBackStack == 0) {
            listOf(backStacks[0])
        } else {
            listOf(backStacks[0]) + listOf(backStacks[currentBackStack])
        }

        return activeBackStacks.flatMap { backStack ->
            backStack.map { entryProvider(it) }
        }
    }
}
