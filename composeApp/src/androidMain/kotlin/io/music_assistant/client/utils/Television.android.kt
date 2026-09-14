package io.music_assistant.client.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun isTelevisionDevice(): Boolean {
    val configuration = LocalContext.current.resources.configuration
    return (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
}
