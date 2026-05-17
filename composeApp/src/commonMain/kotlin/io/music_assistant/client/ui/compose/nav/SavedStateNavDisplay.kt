package io.music_assistant.client.ui.compose.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * Handles boilerplate to make sure that [rememberSaveable] works as expected - state is restored
 * when returning to items in the back stack.
 */
@Composable
fun SavedStateNavDisplay(entries: List<NavEntry<NavKey>>, onBack: () -> Unit) {
    val entryDecorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    NavDisplay(
        entries = rememberDecoratedNavEntries(
            entryDecorators = entryDecorators,
            entries = entries,
        ),
        onBack = onBack,
    )
}
