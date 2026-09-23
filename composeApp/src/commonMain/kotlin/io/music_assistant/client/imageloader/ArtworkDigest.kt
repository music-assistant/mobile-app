package io.music_assistant.client.imageloader

import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

private const val BYTE_MASK = 0xff
private const val NIBBLE_MASK = 0x0f
private const val NIBBLE_SHIFT = 4
private const val HEX_DIGITS = "0123456789abcdef"
private const val ARTWORK_LOG_ID_LENGTH = 12

private val artworkLog = Logger.withTag("Artwork")

internal object ArtworkDiagnostics {
    fun logProvenance(provenance: String, keyId: String, digest: String? = null) {
        artworkLog.d {
            "artwork provenance=$provenance keyId=${keyId.artworkLogId()}" +
                (digest?.let { " digest=${it.artworkLogId()}" } ?: "")
        }
    }

    fun logJoined(keyId: String) = logProvenance("joined", keyId)

    fun cacheFailure(kind: String, keyId: String) {
        artworkLog.d { "artwork cache failure=$kind keyId=${keyId.artworkLogId()}" }
    }

    fun cacheExpired(keyId: String) {
        artworkLog.d { "artwork cache expiry=expired keyId=${keyId.artworkLogId()}" }
    }
}

private fun String.artworkLogId(): String = removePrefix("artwork-v1-").take(ARTWORK_LOG_ID_LENGTH)

internal suspend fun artworkSha256Hex(bytes: ByteArray): String =
    CryptographyProvider.Default.get(SHA256).hasher().hash(bytes).toHex()

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[value shr NIBBLE_SHIFT])
        append(HEX_DIGITS[value and NIBBLE_MASK])
    }
}
