package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Explicit D-pad navigation between a fixed set of focus targets.
 *
 * Compose's geometric focus search does not reliably move focus between siblings on Android TV
 * (a remote's DOWN from a centered button can report success while leaving focus untouched), so TV
 * forms declare each focusable element's directional neighbours instead of trusting geometry.
 *
 * Rather than intercepting key events and requesting focus by hand, [modifierFor] wires each
 * target's neighbours into the focus graph through [androidx.compose.ui.focus.FocusProperties] —
 * the Compose-recommended mechanism for explicit traversal order — and the framework's normal key
 * dispatch honours those links. This keeps navigation working with Lazy-list scrolling, focus
 * groups, and IME actions instead of fighting them.
 *
 * Text fields are the one gap: a focused TextField consumes D-pad in its own key handling before
 * the framework can honour the links on the field's node, so navigation out of a text field never
 * reaches the focus system. [modifierFor] therefore also routes directional keys through the same
 * links in the preview phase (which runs before the field's internal handler) and consumes them —
 * the one place this class still moves focus itself, because the field swallows the event first.
 *
 * Targets are identified by string keys so requesters survive recomposition and content
 * reordering. Links are declared by the screen (per tab / layout) so the navigation order never
 * drifts from the actual layout.
 */
class TvFocusFlow {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /**
     * Id of the target whose node currently holds focus, updated by [modifierFor]. Screens use this
     * to tell "focus is on a declared link" from "focus is on a foreign node (a clipped button, the
     * top bar) or nowhere" so they can re-land D-pad on the primary target instead of letting the
     * remote go dead.
     */
    var focusedTarget: String? = null

    /** The directional neighbours of a single focus target. */
    data class Links(
        val up: String? = null,
        val down: String? = null,
        val left: String? = null,
        val right: String? = null,
    )

    /** Move focus to [target] directly. Used to land initial focus on screen entry. */
    fun requestFocus(target: String): Boolean = requesterFor(target).requestFocus()

    /** Attach [target] and wire its directional neighbours into the focus graph. */
    fun modifierFor(target: String, links: Links): Modifier = Modifier
        .focusRequester(requesterFor(target))
        .focusProperties {
            // Directional links default to the (no-op) framework fallback, which is the geometric
            // search; set one only when the screen declares an explicit neighbour.
            links.up?.let { up = requesterFor(it) }
            links.down?.let { down = requesterFor(it) }
            links.left?.let { left = requesterFor(it) }
            links.right?.let { right = requesterFor(it) }
        }
        .onFocusChanged { state ->
            if (state.hasFocus) focusedTarget = target
        }
        .onPreviewKeyEvent { event ->
            // A focused text field consumes D-pad for its own cursor handling before the framework
            // honours the FocusProperties links above, so navigation out of a text field never
            // reaches the focus system. Route directional keys through the same links in the
            // preview phase (which runs before the field's internal handler) and consume them.
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val next = when (event.key) {
                Key.DirectionUp -> links.up
                Key.DirectionDown -> links.down
                Key.DirectionLeft -> links.left
                Key.DirectionRight -> links.right
                else -> null
            } ?: return@onPreviewKeyEvent false
            requesterFor(next).requestFocus()
        }

    private fun requesterFor(target: String): FocusRequester =
        requesters.getOrPut(target) { FocusRequester() }
}

@Composable
fun rememberTvFocusFlow(): TvFocusFlow = remember { TvFocusFlow() }

/**
 * Attach TV D-pad links for [id] when a focus flow is wired up for the current screen (phones and
 * screens without a declared chain pass [flow] as null and stay with the framework default).
 */
fun Modifier.tvFocus(
    flow: TvFocusFlow?,
    links: Map<String, TvFocusFlow.Links>,
    id: String,
): Modifier = if (flow != null && links.containsKey(id)) {
    flow.modifierFor(id, links.getValue(id))
} else {
    this
}
