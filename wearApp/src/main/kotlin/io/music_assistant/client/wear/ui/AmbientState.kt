// Compose layout values (the burn-in shift) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Whether the watch is awake or showing its always-on screen, and what that screen has to
 * respect.
 *
 * The flags are the platform's own (`AmbientLifecycleObserver.AmbientDetails`) and are passed
 * through rather than interpreted at the edge: what to do about a panel that burns in, or one
 * that can only show a handful of colours, is a question for the screen that draws.
 */
sealed interface AmbientState {

    /** The screen is awake: full brightness, animation allowed, touch reaches the app. */
    data object Interactive : AmbientState

    /**
     * The always-on screen. [updateCount] counts the updates the system has asked for, which
     * arrive about once a minute and are the only beat an ambient screen gets.
     */
    data class Ambient(
        val burnInProtectionRequired: Boolean,
        val lowBitAmbient: Boolean,
        val updateCount: Int = 0,
    ) : AmbientState

    val isAmbient: Boolean get() = this is Ambient

    /**
     * How far to shift the content this minute, so that a pixel held bright for an hour is
     * not held bright in the same place for the next one.
     *
     * The walk is a small square rather than a random jump: the content moves far enough to
     * spread the wear and little enough that a glance does not read it as the screen
     * twitching. A panel that does not ask for the protection does not pay for it, and an
     * awake screen never moves at all.
     */
    val burnInOffset: DpOffset
        get() = (this as? Ambient)
            ?.takeIf { it.burnInProtectionRequired }
            ?.let { BURN_IN_WALK[it.updateCount.mod(BURN_IN_WALK.size)] }
            ?: DpOffset.Zero
}

/**
 * How far the ambient content shifts off centre. Wear's own guidance is a couple of pixels a
 * minute; 4 dp is that on the densities this app runs at, and still inside the margin the
 * labels keep from the glass.
 */
private val BURN_IN_SHIFT = 4.dp

/** The four corners of the shift, walked one step per ambient update. */
private val BURN_IN_WALK = listOf(
    DpOffset(0.dp, -BURN_IN_SHIFT),
    DpOffset(BURN_IN_SHIFT, 0.dp),
    DpOffset(0.dp, BURN_IN_SHIFT),
    DpOffset(-BURN_IN_SHIFT, 0.dp),
)

/**
 * The ambient state, read by the screens that draw differently when the wrist is down.
 *
 * It is a composition local and not a parameter because the screens sit under a
 * `SwipeDismissableNavHost`, whose destinations hold the content lambda they were built with.
 * A value captured by such a lambda does not reach the screen when it changes — measured: the
 * ambient state reached `MaWearApp` and stopped there, and the always-on layout never drew. A
 * local is read by the screen itself, so the screen is what gets invalidated.
 */
val LocalAmbientState = compositionLocalOf<AmbientState> { AmbientState.Interactive }
