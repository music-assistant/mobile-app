package io.music_assistant.client.ui.compose.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.toClipEntry

actual fun plainTextClipEntry(text: String): ClipEntry = ClipData.newPlainText(text, text).toClipEntry()
