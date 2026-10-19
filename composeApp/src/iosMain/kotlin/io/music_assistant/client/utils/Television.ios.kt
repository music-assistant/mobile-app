package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

// Apple TV is not a build target; iOS hardware is always touch-driven.
@Composable
actual fun isTelevisionDevice(): Boolean = false
