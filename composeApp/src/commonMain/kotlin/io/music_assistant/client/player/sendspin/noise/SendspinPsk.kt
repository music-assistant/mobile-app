package io.music_assistant.client.player.sendspin.noise

import io.music_assistant.client.player.sendspin.noise.crypto.NoiseCrypto

/**
 * The three Sendspin PSK categories share one `psk_id` namespace; the client
 * stores each PSK tagged with its category, and on a `psk_id` match the
 * category determines trust handling.
 */
enum class PskCategory {
    /** The published sentinel constant — no authentication on its own. */
    SENTINEL,

    /** This device's per-device Pairing PSK. */
    PAIRING,

    /** A per-server long-term PSK persisted with the server's `server_id`. */
    LONG_TERM_STORED,

    /** A shared long-term PSK persisted without a bound `server_id`. */
    LONG_TERM_SHARED,
}

/**
 * One PSK the client is willing to complete a handshake with. [serverId] is
 * set only for [PskCategory.LONG_TERM_STORED] records, where a post-match
 * check requires it to equal the `server_id` from `server/init`.
 */
class PskCandidate(
    val psk: ByteArray,
    val category: PskCategory,
    val serverId: String? = null,
)

object SendspinPsk {
    /** Label prefixed to a PSK when deriving its `psk_id`. */
    private val PSK_ID_LABEL = "sendspin-psk-id-v1".encodeToByteArray()

    /** Label whose SHA-256 is the sentinel PSK. */
    internal val SENTINEL_LABEL = "sendspin-sentinel-psk-v1".encodeToByteArray()

    /** `SHA-256("sendspin-sentinel-psk-v1")`, a published constant. */
    val SENTINEL_PSK: ByteArray = byteArrayOf(
        0x1b, 0x5e, 0x24, 0xdb.toByte(), 0xc1.toByte(), 0xae.toByte(), 0xd9.toByte(), 0x5f,
        0xc2.toByte(), 0xa5.toByte(), 0xa3.toByte(), 0x38, 0xa9.toByte(), 0x0c, 0x05, 0xdf.toByte(),
        0x44, 0xbd.toByte(), 0x10, 0xf5.toByte(), 0xec.toByte(), 0x1f, 0x4c, 0xd6.toByte(),
        0x6c, 0xbf.toByte(), 0x86.toByte(), 0x27, 0x27, 0x67, 0xb9.toByte(), 0xd3.toByte(),
    )

    /** The sentinel PSK's published `psk_id`. */
    const val SENTINEL_PSK_ID: String = "GFsV9tLaSQm9HcFWpKsgYQOr7wFTvNUtkmFwuVz3zoo"

    /** `psk_id = base64url(SHA-256("sendspin-psk-id-v1" || PSK))`, 43 chars. */
    suspend fun pskId(crypto: NoiseCrypto, psk: ByteArray): String =
        SendspinBase64.encode(crypto.sha256(PSK_ID_LABEL + psk))
}
