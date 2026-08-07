package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import kotlinx.coroutines.awaitCancellation

/**
 * Android TV helper: keep the software keyboard closed while the D-pad moves across text fields.
 *
 * A TextField requests the input method as soon as it gains focus, so on a TV remote merely
 * navigating DOWN into the login form pops the Leanback keyboard — which then swallows every
 * directional press (focus gets stuck on the field) and can even type stray characters into the
 * focused field.
 *
 * [TvTextInputGuard] intercepts those requests: while [editing] is false it holds the session open
 * without ever showing the keyboard, so the field stays focusable and the D-pad chain keeps
 * working. Pair it with [tvSelectToEdit] on each field: an explicit CENTER/ENTER press sets
 * [editing], which restarts the session through the now-forwarding interceptor and pops the
 * keyboard exactly when the user asks for it; losing focus closes it again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvTextInputGuard(
    enabled: Boolean,
    editing: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            if (editing) {
                nextHandler.startInputMethod(request)
            } else {
                awaitCancellation()
            }
        },
        content = content,
    )
}

/**
 * Attach to a TextField that lives inside a [TvTextInputGuard]: an explicit select press (CENTER /
 * ENTER on the remote) opens the keyboard for the focused field, which otherwise stays closed while
 * the D-pad traverses it. Losing focus closes the keyboard again.
 *
 * The handler reacts while the field's subtree holds focus ([androidx.compose.ui.focus.FocusState.isFocused],
 * which covers the field's internal text node). Focusable siblings of the field — like a password
 * visibility toggle placed next to the field — are not descendants, so they never trip the handler
 * and always receive their own select presses.
 */
fun Modifier.tvSelectToEdit(editing: MutableState<Boolean>): Modifier = composed {
    var editingEnabled by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (editingEnabled &&
            event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
        ) {
            editing.value = true
            true
        } else {
            false
        }
    }.onFocusChanged { state ->
        editingEnabled = state.isFocused
        if (!state.isFocused) editing.value = false
    }
}
