package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.api.WireVersion

/**
 * Message-type framing and fragmentation for the encrypted Sendspin channel.
 *
 * Once a connection is in Noise transport mode, every WebSocket binary frame
 * carries one Noise ciphertext. After AEAD decryption, byte 0 of the plaintext
 * is a uint8 message type:
 *
 * - `0` — JSON message body (UTF-8)
 * - `1` — fragmentation on spec 1.0.0-rc1 (reserved on the 9.x wire)
 * - `2` / `3` — fragmentation on the 9.x wire (reserved on spec 1.0.0-rc1)
 * - `4..7` — player-role audio
 * - other values — further roles, reserved ranges, application-specific roles
 *
 * A single Noise transport message is capped at 65535 bytes; both Sendspin
 * cipher suites spend 16 bytes on the AEAD tag and 1 byte on the message type,
 * leaving at most 65518 bytes of application payload per frame. Larger
 * messages are split across frames. On the 9.x wire:
 *
 * - fragment-more, opening a fragmented message: `[2][origType][data]`
 * - fragment-more, continuation: `[2][data]`
 * - fragment-end: `[3][data]`
 *
 * On spec 1.0.0-rc1 one type with a flags byte (bit 1 = first, bit 0 = last,
 * bits 2-7 reserved and zero):
 *
 * - first fragment: `[1][flags][origType][data]`
 * - later fragments: `[1][flags][data]`
 *
 * Only one fragmented message may be in flight per direction, and no other
 * frame may interleave with it. Malformed sequences are protocol errors and
 * must close the connection. A reserved type on either wire is an ordinary
 * unimplemented message that the receiver ignores.
 */
internal object NoiseFraming {
    const val TYPE_JSON: Int = 0
    const val TYPE_FRAGMENT: Int = 1
    const val TYPE_FRAGMENT_MORE: Int = 2
    const val TYPE_FRAGMENT_END: Int = 3

    const val FLAG_LAST: Int = 0x01
    const val FLAG_FIRST: Int = 0x02
    private const val FLAG_RESERVED_MASK: Int = 0xFC

    /** Spec 1.0.0-rc1 fragment headers: `[1][flags][origType]` first, `[1][flags]` after. */
    private const val FIRST_FRAGMENT_HEADER: Int = 3
    private const val LATER_FRAGMENT_HEADER: Int = 2

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

    private fun isFragmentType(wire: WireVersion, type: Int): Boolean = when (wire) {
        WireVersion.LEGACY_9 -> type == TYPE_FRAGMENT_MORE || type == TYPE_FRAGMENT_END
        WireVersion.SPEC_1_0 -> type == TYPE_FRAGMENT
    }

    /** Frame plaintexts for one message, fragmenting past [MAX_UNFRAGMENTED_PAYLOAD]. */
    fun encode(wire: WireVersion, type: Int, payload: ByteArray): List<ByteArray> {
        require(type in 0..255) { "message type out of range: $type" }
        require(!isFragmentType(wire, type)) { "fragment types cannot be sent as application message types" }
        if (payload.size <= MAX_UNFRAGMENTED_PAYLOAD) {
            val frame = ByteArray(1 + payload.size)
            frame[0] = type.toByte()
            payload.copyInto(frame, 1)
            return listOf(frame)
        }
        return when (wire) {
            WireVersion.LEGACY_9 -> encodeLegacyFragments(type, payload)
            WireVersion.SPEC_1_0 -> encodeFragments(type, payload)
        }
    }

    private fun encodeLegacyFragments(type: Int, payload: ByteArray): List<ByteArray> {
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

    private fun encodeFragments(type: Int, payload: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        // First fragment carries [1][flags][origType][data].
        run {
            val dataLen = minOf(MAX_FRAME_PLAINTEXT - FIRST_FRAGMENT_HEADER, payload.size)
            val frame = ByteArray(FIRST_FRAGMENT_HEADER + dataLen)
            frame[0] = TYPE_FRAGMENT.toByte()
            frame[1] = FLAG_FIRST.toByte()
            frame[2] = type.toByte()
            payload.copyInto(frame, FIRST_FRAGMENT_HEADER, 0, dataLen)
            frames.add(frame)
            offset = dataLen
        }
        // Later fragments carry [1][flags][data]; the last one sets FLAG_LAST.
        while (true) {
            val remaining = payload.size - offset
            val dataLen = minOf(MAX_FRAME_PLAINTEXT - LATER_FRAGMENT_HEADER, remaining)
            val last = dataLen == remaining
            val frame = ByteArray(LATER_FRAGMENT_HEADER + dataLen)
            frame[0] = TYPE_FRAGMENT.toByte()
            frame[1] = (if (last) FLAG_LAST else 0).toByte()
            payload.copyInto(frame, LATER_FRAGMENT_HEADER, offset, offset + dataLen)
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
    class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Per-direction decoder over decrypted frame plaintexts. Returns null while a
     * fragmented message is being reassembled; throws [ProtocolException] on the
     * spec's malformed sequences.
     */
    class Decoder(private val wire: WireVersion) {
        private var inFlightType: Int = -1
        private var buffer: MutableList<ByteArray>? = null

        fun decode(plaintext: ByteArray): Message? {
            if (plaintext.isEmpty()) throw ProtocolException("empty frame plaintext")
            val type = plaintext[0].toInt() and 0xFF
            if (!isFragmentType(wire, type)) {
                if (buffer != null) {
                    throw ProtocolException("non-fragment frame (type $type) while a fragmented message is in flight")
                }
                return Message(type, plaintext.copyOfRange(1, plaintext.size))
            }
            return when (wire) {
                WireVersion.LEGACY_9 -> decodeLegacyFragment(type, plaintext)
                WireVersion.SPEC_1_0 -> decodeFragment(plaintext)
            }
        }

        private fun decodeLegacyFragment(type: Int, plaintext: ByteArray): Message? = when (type) {
            TYPE_FRAGMENT_MORE -> {
                if (buffer == null) {
                    startFragment(plaintext, origTypeIndex = 1)
                } else {
                    append(plaintext.copyOfRange(1, plaintext.size))
                }
                null
            }

            else -> {
                if (buffer == null) throw ProtocolException("fragment-end with no fragmented message in flight")
                append(plaintext.copyOfRange(1, plaintext.size))
                complete()
            }
        }

        private fun decodeFragment(plaintext: ByteArray): Message? {
            if (plaintext.size < 2) throw ProtocolException("fragment missing flags byte")
            val flags = plaintext[1].toInt() and 0xFF
            if (flags and FLAG_RESERVED_MASK != 0) throw ProtocolException("fragment with reserved flag bits $flags")
            val first = flags and FLAG_FIRST != 0
            val inFlight = buffer != null
            when {
                first && inFlight -> throw ProtocolException("first fragment while a fragmented message is in flight")
                !first && !inFlight -> throw ProtocolException(
                    "continuation fragment with no fragmented message in flight",
                )
                first -> startFragment(plaintext, origTypeIndex = 2)
                else -> append(plaintext.copyOfRange(2, plaintext.size))
            }
            return if (flags and FLAG_LAST != 0) complete() else null
        }

        private fun startFragment(plaintext: ByteArray, origTypeIndex: Int) {
            if (plaintext.size <= origTypeIndex) throw ProtocolException("opening fragment missing origType")
            val origType = plaintext[origTypeIndex].toInt() and 0xFF
            if (isFragmentType(wire, origType)) {
                throw ProtocolException("fragmented message with fragment origType $origType")
            }
            inFlightType = origType
            buffer = mutableListOf(plaintext.copyOfRange(origTypeIndex + 1, plaintext.size))
        }

        private fun append(data: ByteArray) {
            buffer?.add(data) ?: throw ProtocolException("fragment data with no fragmented message in flight")
        }

        private fun complete(): Message {
            val parts = buffer ?: throw ProtocolException("completing with no fragmented message in flight")
            val payload = ByteArray(parts.sumOf { it.size })
            var offset = 0
            for (part in parts) {
                part.copyInto(payload, offset)
                offset += part.size
            }
            val message = Message(inFlightType, payload)
            buffer = null
            inFlightType = -1
            return message
        }
    }
}
