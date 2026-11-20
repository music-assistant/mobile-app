package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.runtime.Composable

/** iOS has no playback capture, and the share action never shows there. */
@Composable
actual fun rememberDeviceAudioShareLauncher(): DeviceAudioShareLauncher = DeviceAudioShareLauncher { false }
