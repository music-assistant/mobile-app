package io.music_assistant.client.utils

sealed interface AuthProcessState {
    object NotStarted : AuthProcessState
    object InProgress : AuthProcessState
    object LoggedOut : AuthProcessState
    data class Failed(val reason: String, val savedToken: SavedToken = SavedToken.CLEAR) : AuthProcessState
}

/** What a failed auth does with the saved token of the server. */
enum class SavedToken {
    /** The server refused the token, or a manual login failed. */
    CLEAR,

    /**
     * An automatic login got no reply. The server did not refuse the token, so it stays, and the
     * next request or foreground retries it.
     */
    KEEP_AND_RETRY,
}
