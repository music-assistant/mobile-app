package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import io.music_assistant.client.utils.isTelevisionDevice

/**
 * Clears focus (and so hides the keyboard) when the user drags the content vertically.
 *
 * Only a scroll made while a pointer is pressed counts. Compose dispatches semantics scrolls and
 * bring-into-view animations as [NestedScrollSource.UserInput] too; clearing focus on those lets
 * the platform re-focus the first focusable child, whose bring-into-view scrolls again, endlessly.
 *
 * Android TV hits the same endless-reclear loop from a different direction: D-pad-driven focus
 * changes can themselves trigger a "bring newly-focused item into view" scroll dispatched through
 * this same nested-scroll connection, and since nothing there is a pointer press, the isPressed
 * guard above doesn't catch it -- verified live via CategoryRow's first search result never
 * staying focused (isFocused flipped true then false ~20ms later, in an infinite loop, only
 * stopping once this modifier was removed from the list it was scrolling into view within). TV
 * has no touch gesture for this to guard in the first place, so the function no-ops there instead.
 */
fun Modifier.clearFocusOnScroll(): Modifier = composed {
    if (isTelevisionDevice()) return@composed this
    val focusManager = LocalFocusManager.current
    val pointer = remember { PointerPressState() }
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (pointer.isPressed && source == NestedScrollSource.UserInput && available.y != 0f) {
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    pointerInput(pointer) {
        awaitPointerEventScope {
            while (true) {
                // Initial pass: observe before the scrollable consumes, and never consume here.
                pointer.isPressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
            }
        }
    }.nestedScroll(connection)
}

private class PointerPressState {
    var isPressed = false
}
