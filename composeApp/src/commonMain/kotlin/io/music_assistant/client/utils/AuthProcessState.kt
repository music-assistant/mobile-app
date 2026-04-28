package io.music_assistant.client.utils

sealed interface AuthProcessState {
    object NotStarted : AuthProcessState
    object InProgress : AuthProcessState
    object LoggedOut : AuthProcessState
    data class Failed(val reason: String) : AuthProcessState
}
