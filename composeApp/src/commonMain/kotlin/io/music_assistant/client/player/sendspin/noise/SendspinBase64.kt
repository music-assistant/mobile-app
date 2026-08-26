package io.music_assistant.client.player.sendspin.noise

import kotlin.io.encoding.Base64

/**
 * Base64url without padding, the encoding Sendspin uses for identities
 * (`client_id`/`server_id`), `psk_id` values, and `noise/handshake` data.
 */
object SendspinBase64 {
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun encode(bytes: ByteArray): String = codec.encode(bytes)

    /** Decodes strictly; throws [IllegalArgumentException] on malformed input. */
    fun decode(text: String): ByteArray = codec.decode(text)

    fun decodeOrNull(text: String): ByteArray? = try {
        codec.decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
