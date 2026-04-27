package io.music_assistant.client.player.sendspin.model

enum class BinaryMessageType(val value: UByte) {
    // Player role (4-7)
    AUDIO_CHUNK(4u),

    // Artwork role (8-11) - channels 0-3
    ARTWORK_CHANNEL_0(8u),
    ARTWORK_CHANNEL_1(9u),
    ARTWORK_CHANNEL_2(10u),
    ARTWORK_CHANNEL_3(11u),

    // Visualizer role (16-23)
    VISUALIZER_DATA(16u),
    ;

    companion object {
        fun fromValue(value: UByte): BinaryMessageType? {
            return entries.find { it.value == value }
        }
    }
}

data class BinaryMessage(
    val type: BinaryMessageType,
    val timestamp: Long,
    val data: ByteArray,
) {
    companion object {
        // 1 byte type + 8 byte big-endian int64 timestamp
        private const val HEADER_SIZE = 9

        fun decode(data: ByteArray): BinaryMessage? {
            if (data.size < HEADER_SIZE) return null

            val typeValue = data[0].toUByte()
            val type = BinaryMessageType.fromValue(typeValue) ?: return null

            // Extract big-endian int64 from bytes 1-8
            val timestamp = (0..7).fold(0L) { acc, i ->
                (acc shl 8) or (data[1 + i].toLong() and 0xFF)
            }

            // Validate timestamp is non-negative
            if (timestamp < 0) return null

            val payload = data.copyOfRange(HEADER_SIZE, data.size)

            return BinaryMessage(type, timestamp, payload)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryMessage) return false
        return type == other.type &&
                timestamp == other.timestamp &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
