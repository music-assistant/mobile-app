package io.music_assistant.client.ui.compose.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

class MultiBackStack(private val backStacks: List<NavBackStack<NavKey>>) {

    var currentBackStack by mutableStateOf(0)

    fun add(element: NavKey) {
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

    @Composable
    fun toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val activeBackStacks = if (currentBackStack == 0) {
            listOf(backStacks[0])
        } else {
            listOf(backStacks[0]) + listOf(backStacks[currentBackStack])
        }

        val saveableStateHolderForHome = rememberSaveableStateHolder()
        return activeBackStacks.flatMap {
            rememberDecoratedNavEntries(
                backStack = it,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(saveableStateHolderForHome)
                ),
                entryProvider = entryProvider
            )
        }
    }
}
