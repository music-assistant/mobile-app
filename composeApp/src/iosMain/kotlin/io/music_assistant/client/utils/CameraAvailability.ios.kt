package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

// All supported iOS hardware has a camera; nothing to gate here.
@Composable
actual fun hasCamera(): Boolean = true
