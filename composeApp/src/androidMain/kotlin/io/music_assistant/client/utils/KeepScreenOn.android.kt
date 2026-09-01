package io.music_assistant.client.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    // ModalBottomSheet composes its content in its own dialog window, so the local
    // context is a wrapper: unwrap it instead of casting, and hold the flag on the
    // activity window so it outlives the sheet window.
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(window, enabled) {
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
