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

/**
 * Clears focus (and so hides the keyboard) when the user drags the content vertically.
 *
 * Only a scroll made while a pointer is pressed counts. Compose dispatches semantics scrolls and
 * bring-into-view animations as [NestedScrollSource.UserInput] too; clearing focus on those lets
 * the platform re-focus the first focusable child, whose bring-into-view scrolls again, endlessly.
 */
fun Modifier.clearFocusOnScroll(): Modifier = composed {
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
