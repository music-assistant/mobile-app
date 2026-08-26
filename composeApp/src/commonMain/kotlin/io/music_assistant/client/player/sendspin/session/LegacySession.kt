package io.music_assistant.client.player.sendspin.session

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.ServerHelloMessage
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Connection-establishment inputs for the legacy (unencrypted) protocol. */
class LegacySessionConfig(
    val requiresAuth: Boolean,
    /** Pre-encoded `auth` message for the proxy pre-exchange, when required. */
    val authJson: String?,
    /** Pre-encoded `client/hello`, exactly as the legacy wire expects it. */
    val helloJson: String,
)

/**
 * The pre-encryption Sendspin protocol, byte-identical on the wire to the
 * behavior older Music Assistant servers expect: optional proxy
 * `auth`/`auth_ok` pre-exchange, cleartext JSON text frames, and raw binary
 * frames that are audio chunks verbatim.
 */
internal class LegacySession(
    transport: SendspinTransport,
    private val config: LegacySessionConfig,
) : AbstractSendspinSession(transport) {
    private val logger = Logger.withTag("LegacySession")

    override val sender: SendspinOutboundSender = LegacySender()

    private inner class LegacySender : SendspinOutboundSender {
        private val mutex = Mutex()

        override suspend fun sendJson(json: String) {
            mutex.withLock { transport.sendText(json) }
        }
    }

    override suspend fun runEpoch(
        connected: InboundTransportEvent.Connected,
        queue: ReceiveChannel<InboundTransportEvent>,
    ): InboundTransportEvent? {
        val epoch = connected.epoch
        var awaitingAuthOk = false
        val authJson = config.authJson.takeIf { config.requiresAuth }

        emitEvent(SessionEvent.Negotiating(authenticating = authJson != null))
        try {
            if (authJson != null) {
                awaitingAuthOk = true
                transport.sendText(authJson)
            } else {
                transport.sendText(config.helloJson)
            }
        } catch (e: Exception) {
            // Transport closed during the handshake; the reconnect machinery
            // (or a Disconnected event) follows on the queue.
            logger.w { "Failed to send auth/hello (transport closed during handshake): ${e.message}" }
        }

        while (true) {
            when (val step = nextStep(queue, epoch)) {
                is EpochStep.Text -> {
                    awaitingAuthOk = handleText(step.text, awaitingAuthOk, connected.isReconnect)
                }

                // Every raw binary frame on the legacy wire is an audio chunk,
                // passed through byte-identical.
                is EpochStep.Binary -> forwardAudio(step.bytes)

                is EpochStep.Control -> return step.event
            }
        }
    }

    private suspend fun handleText(
        text: String,
        awaitingAuthOk: Boolean,
        isReconnectEpoch: Boolean,
    ): Boolean {
        val type = try {
            myJson.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }

        when {
            type == "auth_ok" && awaitingAuthOk -> {
                logger.i { "Received auth_ok - authentication successful" }
                try {
                    transport.sendText(config.helloJson)
                } catch (e: Exception) {
                    logger.w { "Failed to send hello after auth_ok: ${e.message}" }
                }
                return false
            }

            type == "server/hello" -> {
                forwardApplication(text)
                val payload = try {
                    myJson.decodeFromJsonElement<ServerHelloMessage>(
                        myJson.parseToJsonElement(text).jsonObject,
                    ).payload
                } catch (e: Exception) {
                    logger.e(e) { "Malformed server/hello" }
                    return awaitingAuthOk
                }
                emitEvent(
                    SessionEvent.ProtocolReady(
                        serverId = payload.serverId,
                        serverName = payload.name,
                        matchedPskCategory = null,
                        trustLevel = TrustLevel.NONE,
                        isReconnectEpoch = isReconnectEpoch,
                    ),
                )
            }

            else -> forwardApplication(text)
        }
        return awaitingAuthOk
    }
}
