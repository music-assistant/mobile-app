package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.noise.HandshakeState
import io.music_assistant.sendspin.noise.NoiseFraming
import io.music_assistant.sendspin.noise.NoisePattern
import io.music_assistant.sendspin.noise.NoiseRole
import io.music_assistant.sendspin.noise.NoiseTransport
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * An in-test Noise-initiator Sendspin server over a [FakeTransport]. Every JSON
 * message is built by hand with the field names the spec writes, and client
 * messages are parsed as raw JSON, so the fake shares no wire model with the
 * implementation. It reuses the production Noise core and framing, which the
 * KKpsk2 reference vectors pin. [wire] selects the 9.x or the 1.0.0-rc1 dialect.
 */
internal class FakeNoiseServer(
    private val crypto: NoiseCrypto,
    private val transport: FakeTransport,
    private val serverStatic: X25519KeyPair,
    /** Defaults to the static key the client announces as `client_id` in `client/init`. */
    clientPublicKey: ByteArray? = null,
    private val psk: ByteArray = SendspinPsk.SENTINEL_PSK,
    private val wire: WireVersion = WireVersion.LEGACY_9,
) {
    lateinit var noise: NoiseTransport
    lateinit var handshakeHash: ByteArray
    private var decoder = NoiseFraming.Decoder(wire)
    private var clientStatic: ByteArray? = clientPublicKey

    val serverId: String get() = SendspinBase64.encode(serverStatic.publicKey)

    /** Server clock minus local clock, as reported in every `server/time`. */
    var serverClockOffsetMicros = 0L

    /** While true, [serve] leaves `client/time` unanswered. */
    var silent = false

    /** Every non-probe client message received by [serve], as `type` values. */
    val clientMessageTypes = mutableListOf<String>()

    /** `client/goodbye` reasons received by [serve], in order. */
    val goodbyeReasons = mutableListOf<String?>()

    /** `client/state` payloads received by [serve], in order. */
    val clientStates = mutableListOf<JsonObject>()

    private suspend fun clientText(): String =
        assertIs<Frame.Text>(withTimeout(AWAIT_MILLIS) { transport.outbound.receive() }).text

    /** The `psk_category` a spec server declares for [psk]: the Sentinel is `sn`, any other PSK a long-term one. */
    private fun categoryOf(psk: ByteArray): String = if (psk.contentEquals(SendspinPsk.SENTINEL_PSK)) "sn" else "lt"

    private fun message1Payload(pskId: String, categoryCode: String): String = when (wire) {
        WireVersion.LEGACY_9 -> """{"psk_id":"$pskId"}"""
        WireVersion.SPEC_1_0 -> """{"psk_id":"$pskId","psk_category":"$categoryCode"}"""
    }

    /**
     * Runs the init exchange and the Noise handshake. Message 1 names [pskIdOverride]
     * (or the id of [psk]) under [categoryCode]; message 2 is verified with
     * [verifyWith], which a spec server sets to the Sentinel PSK on its second try.
     */
    suspend fun establish(
        pskIdOverride: String? = null,
        categoryCode: String = categoryOf(psk),
        verifyWith: ByteArray = psk,
    ) {
        val clientInit = clientText()
        val serverInitText = message("server/init") {
            put("server_id", serverId)
            put("version", 1)
        }
        transport.serverSends(serverInitText)
        val remoteStatic = clientStatic
            ?: SendspinBase64.decode(payloadOf(clientInit).getValue("client_id").jsonPrimitive.content)
                .also { clientStatic = it }

        val handshake = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = clientInit.encodeToByteArray() + serverInitText.encodeToByteArray(),
            localStatic = serverStatic,
            remoteStaticPublic = remoteStatic,
            psk = null,
        )
        val pskId = pskIdOverride ?: SendspinPsk.pskId(crypto, psk)
        val message1 = handshake.writeMessage(message1Payload(pskId, categoryCode).encodeToByteArray())
        transport.serverSends(handshakeMessage(message1))
        handshake.providePsk(verifyWith)
        handshake.readMessage(SendspinBase64.decode(handshakeData(clientText())))
        noise = handshake.result!!.transport
        handshakeHash = handshake.result!!.handshakeHash
        decoder = NoiseFraming.Decoder(wire)
    }

    /** Reads `client/init` and answers `server/error` with [reason] instead of `server/init`. */
    suspend fun failInit(reason: String) {
        clientText()
        transport.serverSends(message("server/error") { put("reason", reason) })
    }

    suspend fun sendJson(text: String) {
        NoiseFraming.encode(wire, NoiseFraming.TYPE_JSON, text.encodeToByteArray()).forEach {
            transport.serverSends(noise.encrypt(it))
        }
    }

    /** One unfragmented frame of an arbitrary [type], bypassing the framing encoder's checks. */
    suspend fun sendRawFrame(type: Int, payload: ByteArray) {
        transport.serverSends(noise.encrypt(byteArrayOf(type.toByte()) + payload))
    }

    /** Sends one player-role message: `[8-byte timestamp][4-byte send_ahead on spec 1.0.0-rc1][data]`. */
    suspend fun sendAudio(type: Int, timestamp: Long, data: ByteArray, sendAhead: Long = 0) {
        val header = when (wire) {
            WireVersion.LEGACY_9 -> TIMESTAMP_BYTES
            WireVersion.SPEC_1_0 -> TIMESTAMP_BYTES + SEND_AHEAD_BYTES
        }
        val body = ByteArray(header + data.size)
        for (i in 0 until TIMESTAMP_BYTES) body[i] = (timestamp shr (Byte.SIZE_BITS * (TIMESTAMP_BYTES - 1 - i))).toByte()
        for (i in TIMESTAMP_BYTES until header) {
            body[i] = (sendAhead shr (Byte.SIZE_BITS * (header - 1 - i))).toByte()
        }
        data.copyInto(body, header)
        NoiseFraming.encode(wire, type, body).forEach { transport.serverSends(noise.encrypt(it)) }
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

    /** The next client message must be `client/goodbye`; returns its reason. */
    suspend fun receiveGoodbyeReason(): String? {
        val goodbye = parse(receiveJson())
        assertEquals("client/goodbye", goodbye.getValue("type").jsonPrimitive.content)
        return goodbye["payload"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
    }

    /**
     * The client's half of the Pairing PSK flow: on spec 1.0.0-rc1 a `client/pair-init`
     * carrying [expectedPairingIndex] first, then on both wires `client/pair-finalize`.
     * Returns the minted long-term PSK.
     */
    suspend fun receivePairFinalize(expectedPairingIndex: Int = 1): ByteArray {
        if (wire == WireVersion.SPEC_1_0) {
            val init = parse(receiveJson())
            assertEquals("client/pair-init", init.getValue("type").jsonPrimitive.content)
            assertEquals(
                expectedPairingIndex,
                init.getValue("payload").jsonObject.getValue("pairing_index").jsonPrimitive.int,
            )
        }
        val finalize = parse(receiveJson())
        assertEquals("client/pair-finalize", finalize.getValue("type").jsonPrimitive.content)
        return SendspinBase64.decode(
            finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )
    }

    /** Runs a server-initiated in-band re-handshake to [newPsk]; the hello exchange is the caller's (9.x only). */
    suspend fun rehandshake(newPsk: ByteArray, categoryCode: String = categoryOf(newPsk)) {
        val handshake = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = handshakeHash,
            localStatic = serverStatic,
            remoteStaticPublic = checkNotNull(clientStatic) { "rehandshake before establish" },
            psk = newPsk,
        )
        val pskId = SendspinPsk.pskId(crypto, newPsk)
        val message1 = handshake.writeMessage(message1Payload(pskId, categoryCode).encodeToByteArray())
        sendJson(handshakeMessage(message1))
        // Message 2 arrives under the old transport keys.
        handshake.readMessage(SendspinBase64.decode(handshakeData(receiveJson())))
        noise = handshake.result!!.transport
        handshakeHash = handshake.result!!.handshakeHash
        decoder = NoiseFraming.Decoder(wire)
    }

    suspend fun completeHelloExchange(): String {
        sendJson(message("server/hello") { put("name", "Enc Server") })
        return receiveJson()
    }

    /**
     * Sends `server/activate`. The client decides the outcome: `["playback"]` on
     * a sentinel session is admitted only with unpaired access enabled (else
     * `pairing_required`), `["playback","management"]` on a sentinel is
     * `unauthorized`, `["pairing"]` needs [pairing] with a method.
     */
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

    /** The client's `client/hello` from [bringUp]. */
    var clientHello: String? = null
        private set

    /** Establishment, hello, and a playback activation in one go. */
    suspend fun bringUp() {
        establish()
        clientHello = completeHelloExchange()
        activate()
    }

    /**
     * Answers `client/time` probes instantly with a server clock shifted by
     * [serverClockOffsetMicros] (unless [silent]) and records every other client
     * message. Runs until cancelled or the transport closes.
     */
    fun serve(scope: CoroutineScope): Job = scope.launch {
        while (true) {
            val root = runCatching { parse(receiveJson()) }.getOrNull() ?: return@launch
            val type = root.getValue("type").jsonPrimitive.content
            if (type == "client/time") {
                if (silent) continue
                val t1 = root.getValue("payload").jsonObject.getValue("client_transmitted").jsonPrimitive.long
                sendJson(
                    message("server/time") {
                        put("client_transmitted", t1)
                        // A 1 us one-way delay each way, folded into the server stamps because
                        // virtual time does not pass: the time filter discards a zero max_error.
                        put("server_received", t1 + serverClockOffsetMicros + 1)
                        put("server_transmitted", t1 + serverClockOffsetMicros - 1)
                    },
                )
            } else {
                clientMessageTypes += type
                when (type) {
                    "client/goodbye" ->
                        goodbyeReasons += root["payload"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull

                    "client/state" -> clientStates += root.getValue("payload").jsonObject
                }
            }
        }
    }

    /** Sends a `stream/start` for [codec] at [SAMPLE_RATE] Hz stereo [BIT_DEPTH]-bit. */
    suspend fun startStream(codec: String = "flac") {
        sendJson(
            message("stream/start") {
                putJsonObject("player") {
                    put("codec", codec)
                    put("sample_rate", SAMPLE_RATE)
                    put("channels", 2)
                    put("bit_depth", BIT_DEPTH)
                }
            },
        )
    }

    suspend fun endStream() = sendJson(message("stream/end") {})

    suspend fun clearStream() = sendJson(message("stream/clear") {})

    suspend fun unpair() = sendJson(message("server/unpair") {})

    private fun message(type: String, payload: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
        put("type", type)
        putJsonObject("payload", payload)
    }.toString()

    private fun handshakeMessage(bytes: ByteArray): String =
        message("noise/handshake") { put("data", SendspinBase64.encode(bytes)) }

    private fun handshakeData(text: String): String = payloadOf(text).getValue("data").jsonPrimitive.content

    private fun payloadOf(text: String): JsonObject = parse(text).getValue("payload").jsonObject

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private companion object {
        const val AWAIT_MILLIS = 5_000L
        const val TIMESTAMP_BYTES = 8
        const val SEND_AHEAD_BYTES = 4
        const val SAMPLE_RATE = 48_000
        const val BIT_DEPTH = 16
    }
}
