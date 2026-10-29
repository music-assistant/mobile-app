package io.music_assistant.client.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The activity behind this context. A dialog or a bottom sheet composes in its own window with
 * a wrapped context, so unwrap it instead of casting.
 */
internal fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
