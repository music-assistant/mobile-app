package io.music_assistant.sendspin.connection

import io.music_assistant.sendspin.api.FailureCause
import io.music_assistant.sendspin.session.SessionInfo

internal sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class Connecting(val attempt: Int) : ConnectionState

    /** Hello exchange done. [activated] turns true on the first admissible activation. */
    data class Active(val info: SessionInfo, val activated: Boolean) : ConnectionState

    data class Backoff(val attempt: Int, val reason: DropReason, val retryAtMicros: Long) : ConnectionState

    data class WaitingForNetwork(val attempt: Int, val reason: DropReason) : ConnectionState

    /** No retry. Leaves only when the supervisor is restarted. */
    data class Failed(val cause: FailureCause) : ConnectionState
}

/** Why an attempt ended. Every variant except [Rejected] is retried without limit. */
internal sealed interface DropReason {
    data class ConnectFailed(val cause: Throwable) : DropReason
    data class Lost(val cause: Throwable?) : DropReason
    data object ServerClosed : DropReason
    data class Rejected(val goodbye: String) : DropReason
    data object Silent : DropReason
    data class Protocol(val cause: Throwable) : DropReason
}

/** Thrown inside an attempt when the server stops answering; ends the attempt. */
class ServerSilentException : Exception("server stopped answering")
