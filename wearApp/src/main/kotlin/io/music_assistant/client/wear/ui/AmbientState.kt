package io.music_assistant.client.wear.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the watch is awake or showing its always-on screen, and what that screen has to
 * respect.
 *
 * The flags are the platform's own (`AmbientLifecycleObserver.AmbientDetails`) and are passed
 * through rather than interpreted at the edge: what to do about a panel that can only show a
 * handful of colours is a question for the screen that draws.
 *
 * `burnInProtectionRequired` is deliberately not carried. The system shifts the display
 * itself on a panel that needs it, and an app that also shifts moves the content twice —
 * which is both visible and liable to push something off the edge. What the flag asks of an
 * app is only that it avoid solid bright areas and keep anything that matters clear of the
 * rim, and the always-on layout does both whatever the panel reports.
 */
sealed interface AmbientState {

    /** The screen is awake: full brightness, animation allowed, touch reaches the app. */
    data object Interactive : AmbientState

    /** The always-on screen: dimmed, still, and updated about once a minute. */
    data class Ambient(val lowBitAmbient: Boolean) : AmbientState

    val isAmbient: Boolean get() = this is Ambient
}

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
