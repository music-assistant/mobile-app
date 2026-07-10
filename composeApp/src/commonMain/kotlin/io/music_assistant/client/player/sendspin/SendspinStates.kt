package io.music_assistant.client.player.sendspin

/**
 * Unified Sendspin state machine, as consumed by LocalPlayerController/MainDataSource.
 *
 * The app's SendspinClient adapter maps the library's `ClientState` onto this richer hierarchy.
 */
sealed class SendspinState {
    /** Client created but not yet started. */
    object Idle : SendspinState()

    /** Transport is connecting (initial or after explicit start). */
    object Connecting : SendspinState()

    /** Transport connected; waiting for auth_ok (proxy mode). */
    object Authenticating : SendspinState()

    /** auth_ok received (or direct mode); waiting for server/hello. */
    object Handshaking : SendspinState()

    /** server/hello received — protocol ready, no active stream. */
    data class Ready(val serverId: String, val serverName: String) : SendspinState()

    /** stream/start received — pipeline running, pre-buffering. */
    object Buffering : SendspinState()

    /** Clock sync quality is GOOD — audio is playing in sync. */
    object Synchronized : SendspinState()

    /** Transport reconnecting after a drop. */
    data class Reconnecting(val wasStreaming: Boolean, val attempt: Int) : SendspinState()

    /** Unrecoverable or categorised error. */
    data class Error(val error: SendspinError) : SendspinState()
}
