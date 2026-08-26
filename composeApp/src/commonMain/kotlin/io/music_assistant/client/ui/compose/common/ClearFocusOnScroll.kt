package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import io.music_assistant.client.utils.isTelevisionDevice

// Android TV: this exists to dismiss a focused text field's keyboard when the user scrolls a
// list with touch -- a phone/tablet gesture TV doesn't have. On TV, D-pad-driven focus changes
// can themselves trigger a "bring newly-focused item into view" scroll that gets dispatched
// through this same nested-scroll connection; when that happens the UserInput source check below
// still matches, so this modifier was clearing focus immediately after every D-pad focus grant --
// verified live via CategoryRow's first search result never staying focused (isFocused flipped
// true then false ~20ms later, in an infinite loop, only stopping once this modifier was removed
// from the list it was scrolling into view within). No-op on TV, where it has nothing to guard
// against and only breaks focus-follows-D-pad.
fun Modifier.clearFocusOnScroll(): Modifier = composed {
    if (isTelevisionDevice()) return@composed this
    val focusManager = LocalFocusManager.current
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}
