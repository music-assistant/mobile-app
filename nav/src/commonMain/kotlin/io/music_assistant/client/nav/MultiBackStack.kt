package io.music_assistant.client.nav

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
 *
 * @param backstackAdder determines what happens when [add] is called. By default, new elements
 * are added to the current backstack.
 */
class MultiBackStack<T : NavKey>(
    private val backStacks: List<MutableList<T>>,
    private val backstackAdder: BackstackAdder<T> = object : BackstackAdder<T> {
        override fun add(backStack: MutableList<T>, navKey: T) {
            backStack.add(navKey)
        }
    },
) {
    var currentBackStack by mutableStateOf(0)

    private val roots = backStacks.map { it.first() }

    /**
     * Clear the current back stack and reset it back to its original state. Optionally takes a
     * [ScreenState] that can be reset if the current back stack is already at the root.
     */
    fun resetCurrentBackStack(screenState: ScreenState? = null) {
        backStacks[currentBackStack].apply {
            if (size == 1) {
                screenState?.reset()
            } else {
                clear()
                add(roots[currentBackStack])
            }
        }
    }

    fun add(element: T) {
        backstackAdder.add(backStacks[currentBackStack], element)
    }

    fun removeLastOrNull(): NavKey? {
        val backStack = backStacks[currentBackStack]
        return when {
            backStack.size > 1 -> backStack.removeLastOrNull()
            currentBackStack != 0 -> {
                currentBackStack = 0
                backStacks[0].lastOrNull()
            }
            // A stale or repeated back callback must never remove the Home root.
            else -> null
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

interface ScreenState {
    fun reset()
}

interface BackstackAdder<T : NavKey> {
    fun add(backStack: MutableList<T>, navKey: T)
}
