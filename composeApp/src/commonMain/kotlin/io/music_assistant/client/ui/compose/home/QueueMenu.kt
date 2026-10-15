package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT

/** Keeps the menu scrollable when its popup is measured before receiving a finite height. */
fun Modifier.queueMenuHeight(): Modifier = heightIn(max = MAX_DIALOG_HEIGHT)
