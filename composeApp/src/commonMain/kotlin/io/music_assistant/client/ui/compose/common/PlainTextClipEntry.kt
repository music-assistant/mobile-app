package io.music_assistant.client.ui.compose.common

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry that holds [text]. Compose has no common constructor for one. */
expect fun plainTextClipEntry(text: String): ClipEntry
