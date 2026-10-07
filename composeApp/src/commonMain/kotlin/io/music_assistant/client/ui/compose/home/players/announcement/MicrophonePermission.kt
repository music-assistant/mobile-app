package io.music_assistant.client.ui.compose.home.players.announcement

import androidx.compose.runtime.Composable

/** The record permission that hold-to-talk needs. */
interface MicrophonePermission {
    /** True when granted, without asking. */
    fun isGranted(): Boolean

    /** True when granted. Asks the user while the system still lets the app ask. */
    suspend fun request(): Boolean

    /** Opens this app's page in the system settings, where a refused permission is turned on. */
    fun openSettings()
}

@Composable
expect fun rememberMicrophonePermission(): MicrophonePermission
