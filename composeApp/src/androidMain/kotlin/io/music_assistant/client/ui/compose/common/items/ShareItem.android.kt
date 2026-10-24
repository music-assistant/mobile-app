package io.music_assistant.client.ui.compose.common.items

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareItem(): (SharePayload) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            payload ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload.value)
                putExtra(Intent.EXTRA_SUBJECT, payload.title)
            }
            context.startActivity(Intent.createChooser(intent, payload.title))
        }
    }
}
