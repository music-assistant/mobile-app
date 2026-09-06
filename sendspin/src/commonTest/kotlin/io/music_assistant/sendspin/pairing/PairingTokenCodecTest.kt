package io.music_assistant.sendspin.pairing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingTokenCodecTest {
    // Specification reference vector: client_key = 0x00..0x1f,
    // pairing_psk = 0xe0..0xff.
    private val referenceClientKey = ByteArray(32) { it.toByte() }
    private val referencePsk = ByteArray(32) { (0xe0 + it).toByte() }
    private val referenceToken =
        "SP:0AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYP6BYPC4PSOLZXH5DU6V97M5XXO74HR6LZ7J5PW674PT6X37T6757Y"

    @Test
    fun mintMatchesSpecificationReferenceVector() {
        assertEquals(referenceToken, PairingToken.mint(referenceClientKey, referencePsk))
    }

    @Test
    fun tokenIs107CharactersPlusPrefix() {
        assertEquals(4 + 103, referenceToken.length)
    }

    @Test
    fun decodeReferenceVectorRecoversPayload() {
        val decoded = PairingToken.decode(referenceToken)!!
        assertContentEquals(referenceClientKey, decoded.clientKey)
        assertContentEquals(referencePsk, decoded.pairingPsk)
    }

    @Test
    fun decodeIsLenientWithOperatorInput() {
        val sloppy = "  ${referenceToken.lowercase()}\n"
        val decoded = PairingToken.decode(sloppy)!!
        assertContentEquals(referenceClientKey, decoded.clientKey)
        assertContentEquals(referencePsk, decoded.pairingPsk)
    }

    @Test
    fun decodeToleratesMissingPrefix() {
        val decoded = PairingToken.decode(referenceToken.removePrefix("SP:"))!!
        assertContentEquals(referenceClientKey, decoded.clientKey)
    }

    @Test
    fun decodeRejectsUnknownVersion() {
        assertNull(PairingToken.decode("SP:1" + referenceToken.substring(4)))
    }

    @Test
    fun decodeRejectsWrongPayloadLength() {
        assertNull(PairingToken.decode("SP:0AAAA"))
    }

    @Test
    fun decodeRejectsIllegalCharacters() {
        assertNull(PairingToken.decode(referenceToken.replace('A', '!')))
    }

    @Test
    fun decodeRejectsNonCanonicalTrailingBits() {
        // The 103-char body leaves 3 trailing bits; a canonical encoder zeroes
        // them, so an altered final character that sets them must be rejected
        // rather than aliasing to the same payload.
        assertNull(PairingToken.decode(referenceToken.dropLast(1) + "B"))
    }

    @Test
    fun mintDecodeRoundTripsArbitraryKeys() {
        val key = ByteArray(32) { (it * 7 + 3).toByte() }
        val psk = ByteArray(32) { (255 - it).toByte() }
        val decoded = PairingToken.decode(PairingToken.mint(key, psk))!!
        assertContentEquals(key, decoded.clientKey)
        assertContentEquals(psk, decoded.pairingPsk)
    }
}
