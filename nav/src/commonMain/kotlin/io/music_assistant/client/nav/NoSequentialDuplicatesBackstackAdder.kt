package io.music_assistant.client.nav

import androidx.navigation3.runtime.NavKey

/**
 * Prevents the same entry from being added twice in a row
 */
class NoSequentialDuplicatesBackstackAdder<T : NavKey> : BackstackAdder<T> {
    override fun add(backStack: MutableList<T>, navKey: T) {
        if (backStack.lastOrNull() != navKey) {
            backStack.add(navKey)
        }
    }
}
