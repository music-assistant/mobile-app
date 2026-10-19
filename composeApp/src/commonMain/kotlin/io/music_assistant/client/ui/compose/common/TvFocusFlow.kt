package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * reaches the focus system. [modifierFor] therefore routes directional keys through the same links
 * in the preview phase (which runs before the field's internal handler) and consumes them — the
 * one place this class still moves focus itself, because the field swallows the event first.
 *
 * The preview routing must stay limited to text fields: on this hardware a preview-phase
 * `requestFocus()` on a regular button/row can report success while the framework's focus system
 * moves focus to a *different* node (the geometric fallback takes over and lands randomly). For
 * every non-text target the focus graph above is the reliable mechanism, so [modifierFor] only
 * attaches the preview handler when [textField] is true.
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

    /**
     * Attach [target] and wire its directional neighbours into the focus graph. Pass
     * [textField] = true only for TextField targets: they swallow D-pad for their own cursor
     * handling, so the preview-phase routing is the only way navigation can leave them.
     */
    fun modifierFor(target: String, links: Links, textField: Boolean = false): Modifier {
        val base = Modifier
            .focusRequester(requesterFor(target))
            .focusProperties {
                // Directional links default to the (no-op) framework fallback, which is the
                // geometric search; set one only when the screen declares an explicit neighbour.
                links.up?.let { up = requesterFor(it) }
                links.down?.let { down = requesterFor(it) }
                links.left?.let { left = requesterFor(it) }
                links.right?.let { right = requesterFor(it) }
            }
            .onFocusChanged { state ->
                if (state.hasFocus) focusedTarget = target
            }
        if (!textField) return base
        return base.onPreviewKeyEvent { event ->
            // A focused text field consumes D-pad for its own cursor handling before the framework
            // honours the FocusProperties links above, so navigation out of a text field never
            // reaches the focus system. Route directional keys through the same links in the
            // preview phase (which runs before the field's internal handler) and consume them.
            // Only text fields get this: on this hardware a preview-phase requestFocus on a plain
            // button/row can return success while focus lands on the wrong node.
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
    }

    private fun requesterFor(target: String): FocusRequester =
        requesters.getOrPut(target) { FocusRequester() }
}

@Composable
fun rememberTvFocusFlow(): TvFocusFlow = remember { TvFocusFlow() }

/**
 * Attach TV D-pad links for [id] when a focus flow is wired up for the current screen (phones and
 * screens without a declared chain pass [flow] as null and stay with the framework default). Pass
 * [textField] = true only for TextField targets (see [TvFocusFlow.modifierFor]).
 */
fun Modifier.tvFocus(
    flow: TvFocusFlow?,
    links: Map<String, TvFocusFlow.Links>,
    id: String,
    textField: Boolean = false,
): Modifier = if (flow != null && links.containsKey(id)) {
    then(flow.modifierFor(id, links.getValue(id), textField))
} else {
    this
}

/**
 * Android TV: draw a visible focus ring around a node while it holds D-pad focus. Material3's
 * default focus indication is a subtle tint that reads as a mere background shade change on a TV
 * in a lit room (the settings connection-method tabs), so focused controls that need to be
 * discoverable get an explicit outline instead. The ring only affects the focused state and draws
 * within the node's existing bounds (no layout shift).
 */
fun Modifier.tvFocusRing(
    focusedColor: Color? = null,
    thickness: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    // Set true when attaching this to a non-focusable wrapper around the actual focus target
    // (e.g. a shared item-card Box whose real clickable/focusable content is built by a caller-
    // supplied composable several layers down) rather than to the focusable node itself. hasFocus
    // reports true for the node OR any focused descendant, where isFocused only reports true for
    // this exact node.
    trackDescendants: Boolean = false,
): Modifier = composed {
    val color = focusedColor ?: MaterialTheme.colorScheme.primary
    var isFocused by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        isFocused = if (trackDescendants) state.hasFocus else state.isFocused
    }
        .then(
            if (isFocused) {
                Modifier.border(thickness, color, shape)
            } else {
                Modifier
            },
        )
}
