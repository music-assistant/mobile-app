package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.noise.HandshakeState
import io.music_assistant.sendspin.noise.NoiseFraming
import io.music_assistant.sendspin.noise.NoisePattern
import io.music_assistant.sendspin.noise.NoiseRole
import io.music_assistant.sendspin.noise.NoiseTransport
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.wire.NoiseHandshakeMessage
import io.music_assistant.sendspin.wire.NoiseHandshakePayload
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerInitMessage
import io.music_assistant.sendspin.wire.ServerInitPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * An in-test Noise-initiator Sendspin server over a [FakeTransport]. Reuses the
 * production Noise core and framing, so tests pin session behaviour, not
 * Noise interoperability (the reference vectors cover the core).
 */
class FakeNoiseServer(
    private val crypto: NoiseCrypto,
    private val transport: FakeTransport,
    private val serverStatic: X25519KeyPair,
    private val clientPublicKey: ByteArray,
    private val psk: ByteArray = SendspinPsk.SENTINEL_PSK,
) {
    lateinit var noise: NoiseTransport
    lateinit var handshakeHash: ByteArray
    private var decoder = NoiseFraming.Decoder()

    val serverId: String get() = SendspinBase64.encode(serverStatic.publicKey)

    private suspend fun clientText(): String =
        assertIs<Frame.Text>(withTimeout(AWAIT_MILLIS) { transport.outbound.receive() }).text

    suspend fun establish(pskIdOverride: String? = null) {
        val clientInit = clientText()
        val serverInitText = SendspinJson.encodeToString(ServerInitMessage(payload = ServerInitPayload(serverId, 1)))
        transport.serverSends(serverInitText)

        val handshake = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = clientInit.encodeToByteArray() + serverInitText.encodeToByteArray(),
            localStatic = serverStatic,
            remoteStaticPublic = clientPublicKey,
            psk = psk,
        )
        val pskId = pskIdOverride ?: SendspinPsk.pskId(crypto, psk)
        val message1 = handshake.writeMessage("""{"psk_id":"$pskId"}""".encodeToByteArray())
        transport.serverSends(
            SendspinJson.encodeToString(
                NoiseHandshakeMessage(payload = NoiseHandshakePayload(SendspinBase64.encode(message1))),
            ),
        )
        val message2 = SendspinJson.decodeFromString<NoiseHandshakeMessage>(clientText()).payload.data
        handshake.readMessage(SendspinBase64.decode(message2))
        noise = handshake.result!!.transport
        handshakeHash = handshake.result!!.handshakeHash
        decoder = NoiseFraming.Decoder()
    }

    suspend fun sendJson(text: String) {
        NoiseFraming.encode(NoiseFraming.TYPE_JSON, text.encodeToByteArray()).forEach {
            transport.serverSends(noise.encrypt(it))
        }
    }

    /** Sends one player-role message: `[8-byte timestamp][data]`. */
    suspend fun sendAudio(type: Int, timestamp: Long, data: ByteArray) {
        val body = ByteArray(TIMESTAMP_BYTES + data.size)
        for (i in 0 until TIMESTAMP_BYTES) body[i] = (timestamp shr (Byte.SIZE_BITS * (TIMESTAMP_BYTES - 1 - i))).toByte()
        data.copyInto(body, TIMESTAMP_BYTES)
        NoiseFraming.encode(type, body).forEach { transport.serverSends(noise.encrypt(it)) }
    }

    suspend fun receiveMessage(): NoiseFraming.Message {
        while (true) {
            val frame = withTimeout(AWAIT_MILLIS) { transport.outbound.receive() }
            return decoder.decode(noise.decrypt(assertIs<Frame.Binary>(frame).bytes)) ?: continue
        }
    }

    suspend fun receiveJson(): String {
        val message = receiveMessage()
        assertEquals(NoiseFraming.TYPE_JSON, message.type)
        return message.payload.decodeToString()
    }

    /** Runs a server-initiated in-band re-handshake to [newPsk]. */
    suspend fun rehandshake(newPsk: ByteArray) {
        val handshake = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = handshakeHash,
            localStatic = serverStatic,
            remoteStaticPublic = clientPublicKey,
            psk = newPsk,
        )
        val pskId = SendspinPsk.pskId(crypto, newPsk)
        val message1 = handshake.writeMessage("""{"psk_id":"$pskId"}""".encodeToByteArray())
        sendJson(
            SendspinJson.encodeToString(
                NoiseHandshakeMessage(payload = NoiseHandshakePayload(SendspinBase64.encode(message1))),
            ),
        )
        // Message 2 arrives under the old transport keys.
        val message2 = SendspinJson.decodeFromString<NoiseHandshakeMessage>(receiveJson()).payload.data
        handshake.readMessage(SendspinBase64.decode(message2))
        noise = handshake.result!!.transport
        handshakeHash = handshake.result!!.handshakeHash
        decoder = NoiseFraming.Decoder()
    }

    suspend fun completeHelloExchange(): String {
        sendJson("""{"type":"server/hello","payload":{"name":"Enc Server"}}""")
        return receiveJson()
    }

    suspend fun activate(
        activities: String = """["playback"]""",
        activeRoles: String? = """["player@v1"]""",
        pairing: String? = null,
    ) {
        val fields = buildList {
            add("\"activities\":$activities")
            if (activeRoles != null) add("\"active_roles\":$activeRoles")
            if (pairing != null) add("\"pairing\":$pairing")
        }.joinToString(",")
        sendJson("""{"type":"server/activate","payload":{$fields}}""")
    }

    /** Establishment, hello, and a playback activation in one go. */
    suspend fun bringUp() {
        establish()
        completeHelloExchange()
        activate()
    }

    /** Every non-probe client message received by [serve], as parsed JSON type names. */
    val clientMessageTypes = mutableListOf<String>()

    /**
     * Answers `client/time` probes instantly with a server clock equal to the
     * local clock (offset zero) and records every other client message.
     * Runs until cancelled or the transport closes.
     */
    fun serve(scope: CoroutineScope): Job = scope.launch {
        while (true) {
            val json = runCatching { receiveJson() }.getOrNull() ?: return@launch
            val root = SendspinJson.parseToJsonElement(json).jsonObject
            val type = root.getValue("type").jsonPrimitive.content
            if (type == "client/time") {
                val t1 = root.getValue("payload").jsonObject.getValue("client_transmitted").jsonPrimitive.long
                sendJson(
                    """{"type":"server/time","payload":{"client_transmitted":$t1,"server_received":$t1,"server_transmitted":$t1}}""",
                )
            } else {
                clientMessageTypes += type
            }
        }
    }

    /** Sends a `stream/start` for [codec] at 48 kHz stereo 16-bit. */
    suspend fun startStream(codec: String = "flac") {
        sendJson(
            """{"type":"stream/start","payload":{"player":{"codec":"$codec","sample_rate":48000,"channels":2,"bit_depth":16}}}""",
        )
    }

    private companion object {
        const val AWAIT_MILLIS = 5_000L
        const val TIMESTAMP_BYTES = 8
    }
}
