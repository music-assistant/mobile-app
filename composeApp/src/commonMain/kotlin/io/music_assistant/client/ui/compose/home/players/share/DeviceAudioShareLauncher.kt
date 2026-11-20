package io.music_assistant.client.ui.compose.home.players.share

import androidx.compose.runtime.Composable

/** Starts the platform capture of the device audio, with the consent that the platform wants. */
fun interface DeviceAudioShareLauncher {
    /**
     * Asks the user and starts the capture. False if the user refused, or if the platform
     * cannot capture. Nothing starts and nothing reaches the server in that case.
     */
    suspend fun launch(): Boolean
}

@Composable
expect fun rememberDeviceAudioShareLauncher(): DeviceAudioShareLauncher
