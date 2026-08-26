package io.music_assistant.client.player.sendspin.noise

/**
 * Message-type framing and fragmentation for the encrypted Sendspin channel.
 *
 * Once a connection is in Noise transport mode, every WebSocket binary frame
 * carries one Noise ciphertext. After AEAD decryption, byte 0 of the plaintext
 * is a uint8 message type:
 *
 * - `0` — JSON message body (UTF-8)
 * - `1` — reserved
 * - `2` / `3` — fragmentation (fragment-more / fragment-end)
 * - `4..7` — player-role audio
 * - other values — further roles, reserved ranges, application-specific roles
 *
 * A single Noise transport message is capped at 65535 bytes; both Sendspin
 * cipher suites spend 16 bytes on the AEAD tag and 1 byte on the message type,
 * leaving at most 65518 bytes of application payload per frame. Larger
 * messages are split across frames using the fragment types:
 *
 * - fragment-more, opening a fragmented message: `[2][origType][data]`
 * - fragment-more, continuation: `[2][data]`
 * - fragment-end: `[3][data]`
 *
 * Only one fragmented message may be in flight per direction, and no other
 * frame may interleave with it. Malformed sequences are protocol errors and
 * must close the connection.
 */
object NoiseFraming {
    const val TYPE_JSON: Int = 0
    const val TYPE_FRAGMENT_MORE: Int = 2
    const val TYPE_FRAGMENT_END: Int = 3

    /** Largest Noise transport message allowed by the Noise specification. */
    const val MAX_NOISE_MESSAGE: Int = 65535

    /** AEAD tag size for both defined Sendspin cipher suites. */
    const val AEAD_TAG_SIZE: Int = 16

    /** Largest AEAD plaintext per frame (type byte + payload). */
    const val MAX_FRAME_PLAINTEXT: Int = MAX_NOISE_MESSAGE - AEAD_TAG_SIZE

    /** Largest application payload that fits one non-fragmented frame. */
    const val MAX_UNFRAGMENTED_PAYLOAD: Int = MAX_FRAME_PLAINTEXT - 1

    /** Returns true for message types in the player-role audio range. */
    fun isPlayerAudioType(type: Int): Boolean = type in 4..7

    /** Frame plaintexts for one message, fragmenting past [MAX_UNFRAGMENTED_PAYLOAD]. */
    fun encode(type: Int, payload: ByteArray): List<ByteArray> {
        require(type in 0..255) { "message type out of range: $type" }
        require(type != TYPE_FRAGMENT_MORE && type != TYPE_FRAGMENT_END) {
            "fragment types cannot be sent as application message types"
        }
        if (payload.size <= MAX_UNFRAGMENTED_PAYLOAD) {
            val frame = ByteArray(1 + payload.size)
            frame[0] = type.toByte()
            payload.copyInto(frame, 1)
            return listOf(frame)
        }

        val frames = mutableListOf<ByteArray>()
        var offset = 0
        // Opening fragment-more frame carries [2][origType][data].
        run {
            val dataLen = minOf(MAX_FRAME_PLAINTEXT - 2, payload.size)
            val frame = ByteArray(2 + dataLen)
            frame[0] = TYPE_FRAGMENT_MORE.toByte()
            frame[1] = type.toByte()
            payload.copyInto(frame, 2, 0, dataLen)
            frames.add(frame)
            offset = dataLen
        }
        // Continuations carry [2][data]; the final frame is [3][data].
        while (true) {
            val remaining = payload.size - offset
            val dataLen = minOf(MAX_FRAME_PLAINTEXT - 1, remaining)
            val last = dataLen == remaining
            val frame = ByteArray(1 + dataLen)
            frame[0] = (if (last) TYPE_FRAGMENT_END else TYPE_FRAGMENT_MORE).toByte()
            payload.copyInto(frame, 1, offset, offset + dataLen)
            frames.add(frame)
            offset += dataLen
            if (last) return frames
        }
    }

    /** One fully reassembled application message. */
    class Message(val type: Int, val payload: ByteArray) {
        /** `[type][payload]` — the exact bytes audio consumers expect. */
        fun toFrameBytes(): ByteArray {
            val bytes = ByteArray(1 + payload.size)
            bytes[0] = type.toByte()
            payload.copyInto(bytes, 1)
            return bytes
        }
    }

    /** Raised on malformed frame sequences; the connection must be closed. */
    class ProtocolException(message: String) : Exception(message)

    /**
     * Per-direction decoder over decrypted frame plaintexts. Returns null while a
     * fragmented message is being reassembled; throws [ProtocolException] on the
     * spec's malformed sequences.
     */
    class Decoder {
        private var inFlightType: Int = -1
        private var buffer: MutableList<ByteArray>? = null

        fun decode(plaintext: ByteArray): Message? {
            if (plaintext.isEmpty()) throw ProtocolException("empty frame plaintext")
            val type = plaintext[0].toInt() and 0xFF
            val inFlight = buffer != null
            return when (type) {
                TYPE_FRAGMENT_MORE -> {
                    if (!inFlight) {
                        startFragment(plaintext)
                    } else {
                        buffer!!.add(plaintext.copyOfRange(1, plaintext.size))
                    }
                    null
                }

                TYPE_FRAGMENT_END -> {
                    val parts = buffer
                        ?: throw ProtocolException("fragment-end with no fragmented message in flight")
                    parts.add(plaintext.copyOfRange(1, plaintext.size))
                    val total = parts.sumOf { it.size }
                    val payload = ByteArray(total)
                    var offset = 0
                    for (part in parts) {
                        part.copyInto(payload, offset)
                        offset += part.size
                    }
                    val message = Message(inFlightType, payload)
                    buffer = null
                    inFlightType = -1
                    message
                }

                else -> {
                    if (inFlight) {
                        throw ProtocolException(
                            "non-fragment frame (type $type) while a fragmented message is in flight",
                        )
                    }
                    Message(type, plaintext.copyOfRange(1, plaintext.size))
                }
            }
        }

        private fun startFragment(plaintext: ByteArray) {
            if (plaintext.size < 2) {
                throw ProtocolException("opening fragment missing origType")
            }
            val origType = plaintext[1].toInt() and 0xFF
            if (origType == TYPE_FRAGMENT_MORE || origType == TYPE_FRAGMENT_END) {
                throw ProtocolException("fragmented message with fragment origType $origType")
            }
            inFlightType = origType
            buffer = mutableListOf(plaintext.copyOfRange(2, plaintext.size))
        }
    }
}
