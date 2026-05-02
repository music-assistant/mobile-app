package io.music_assistant.client.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val MAX_DIALOG_HEIGHT = 400.dp
const val INACTIVE_ALPHA = 0.4f

fun Modifier.alphaOn(enabled: Boolean) = alpha(if (enabled) 1f else INACTIVE_ALPHA)
fun Color.alphaOn(enabled: Boolean) = copy(alpha = if (enabled) 1f else INACTIVE_ALPHA)
fun Color.inactive() = alphaOn(false)

const val HUNDRED = 100
const val TEN = 10
const val ONE = 1
