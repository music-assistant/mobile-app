package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

/**
 * Whether this device has a camera available for QR-code scanning. Android TV / Google TV
 * devices generally have none, so callers should hide/disable camera-dependent UI when false
 * rather than opening a scanner that can never get a frame.
 */
@Composable
expect fun hasCamera(): Boolean
