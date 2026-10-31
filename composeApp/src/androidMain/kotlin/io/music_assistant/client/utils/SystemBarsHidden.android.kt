package io.music_assistant.client.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun SystemBarsHidden() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.run {
                show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { systemBarsBehavior = it }
            }
        }
    }
}
