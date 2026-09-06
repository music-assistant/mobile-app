package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.EncryptedDeviceInfo
import io.music_assistant.sendspin.wire.PlayerSupport
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Trust level the client extends to the connected server. */
enum class TrustLevel(val wire: String) {
    NONE("none"),
    USER("user"),
    ;

    companion object {
        fun of(category: PskCategory): TrustLevel = when (category) {
            PskCategory.SENTINEL, PskCategory.PAIRING -> NONE
            PskCategory.LONG_TERM_STORED, PskCategory.LONG_TERM_SHARED -> USER
        }
    }
}

class SessionConfig(
    val deviceName: String,
    val playerSupport: PlayerSupport?,
    val deviceInfo: EncryptedDeviceInfo?,
    /** Bound on a pairing attempt from its first message; the spec recommends 2 min. */
    val pairingAttemptTimeoutMillis: Long = 120_000,
    /** Bound on each establishment step. */
    val stepTimeoutMillis: Long = 30_000,
)

/** Hello exchange complete under the current keys; outbound stays gated until activation. */
data class SessionInfo(
    val serverId: String,
    val serverName: String,
    val matchedPskCategory: PskCategory,
    val trustLevel: TrustLevel,
)

data class Activation(val activities: List<String>, val activeRoles: List<String>)

/** Called on the session's single reader coroutine, in wire order. Keep handlers short. */
internal interface SessionHandler {
    fun onReady(info: SessionInfo)

    fun onActivated(activation: Activation)

    /** Application messages after establishment; never activation, pairing, or management. */
    suspend fun onMessage(message: ServerMessage)

    fun onAudio(chunk: AudioChunk)
}

// Both exceptions implement CopyableThrowable: coroutine stack-trace recovery
// would otherwise guess a constructor and nest or mangle them.

/** The client sent `client/goodbye` with [reason] and closed the connection. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRejected(val reason: String) :
    Exception("session rejected: $reason"), CopyableThrowable<SessionRejected> {
    override fun createCopy(): SessionRejected = SessionRejected(reason)
}

/** The connection ended with a failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportLost(cause: Throwable?) : Exception("transport lost", cause), CopyableThrowable<TransportLost> {
    override fun createCopy(): TransportLost = TransportLost(cause)
}
