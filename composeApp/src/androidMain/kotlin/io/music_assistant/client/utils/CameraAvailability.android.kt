package io.music_assistant.client.utils

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun hasCamera(): Boolean {
    val context = LocalContext.current
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
