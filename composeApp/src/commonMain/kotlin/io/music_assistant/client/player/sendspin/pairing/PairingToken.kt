package io.music_assistant.client.player.sendspin.pairing

/**
 * The Sendspin pairing token: a single case-insensitive ASCII string carrying
 * the client's static public key and its Pairing PSK, transferred out of band
 * (copy/paste, QR) into a server to begin the Pairing PSK flow.
 *
 * ```
 * token   = "SP:" || version || body
 * payload = client_key (32 bytes) || pairing_psk (32 bytes)
 * ```
 *
 * The body is the RFC 4648 base32 encoding of the payload with `=` padding
 * stripped and every `2` transliterated to `9` (avoiding the easily-confused
 * 2/Z pair in transcription), so a version-0 token is 107 characters drawn
 * from the QR alphanumeric set.
 */
object PairingToken {
    private const val PREFIX = "SP:"
    private const val VERSION = '0'
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private const val KEY_SIZE = 32
    private const val PAYLOAD_SIZE = KEY_SIZE * 2
    private const val BASE32_GROUP_CHARS = 8
    private const val BITS_PER_CHAR = 5
    private const val BITS_PER_BYTE = 8
    private const val CHAR_MASK = 0x1F
    private const val BYTE_MASK = 0xFF

    class Decoded(val clientKey: ByteArray, val pairingPsk: ByteArray)

    fun mint(clientPublicKey: ByteArray, pairingPsk: ByteArray): String {
        require(clientPublicKey.size == KEY_SIZE) { "client key must be $KEY_SIZE bytes" }
        require(pairingPsk.size == KEY_SIZE) { "pairing PSK must be $KEY_SIZE bytes" }
        val body = base32Encode(clientPublicKey + pairingPsk)
            .replace("=", "")
            .replace('2', '9')
        return "$PREFIX$VERSION$body"
    }

    /**
     * Lenient decode of operator-supplied input: trims whitespace, is
     * case-insensitive, and tolerates a missing `SP:` prefix. Returns null on
     * anything malformed.
     */
    fun decode(input: String): Decoded? {
        var text = input.trim().uppercase()
        if (text.startsWith(PREFIX)) text = text.removePrefix(PREFIX)
        if (text.isEmpty() || text[0] != VERSION) return null
        val body = text.substring(1).replace('9', '2')
        val remainder = body.length % BASE32_GROUP_CHARS
        val padded = if (remainder == 0) {
            body
        } else {
            body + "=".repeat(BASE32_GROUP_CHARS - remainder)
        }
        val payload = base32Decode(padded) ?: return null
        if (payload.size != PAYLOAD_SIZE) return null
        return Decoded(
            clientKey = payload.copyOfRange(0, KEY_SIZE),
            pairingPsk = payload.copyOfRange(KEY_SIZE, PAYLOAD_SIZE),
        )
    }

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
            bits += BITS_PER_BYTE
            while (bits >= BITS_PER_CHAR) {
                sb.append(BASE32_ALPHABET[(buffer shr (bits - BITS_PER_CHAR)) and CHAR_MASK])
                bits -= BITS_PER_CHAR
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
        }
        while (sb.length % BASE32_GROUP_CHARS != 0) sb.append('=')
        return sb.toString()
    }

    private fun base32Decode(text: String): ByteArray? {
        val stripped = text.trimEnd('=')
        val out = ArrayList<Byte>(stripped.length * BITS_PER_CHAR / BITS_PER_BYTE)
        var buffer = 0
        var bits = 0
        for (char in stripped) {
            val value = BASE32_ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl BITS_PER_CHAR) or value
            bits += BITS_PER_CHAR
            if (bits >= BITS_PER_BYTE) {
                out.add(((buffer shr (bits - BITS_PER_BYTE)) and BYTE_MASK).toByte())
                bits -= BITS_PER_BYTE
            }
        }
        // A canonical encoder leaves leftover bits zero; anything else is malformed.
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }
}
