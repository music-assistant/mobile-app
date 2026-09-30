package io.music_assistant.sendspin.noise.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Android target derives X25519 from BouncyCastle instead of the platform
 * JCA, which registers the algorithm only from API 33. These tests hold that
 * backend to the RFC 7748 vectors and to byte-for-byte agreement with the XDH
 * backend the other targets use.
 */
class BouncyCastleX25519BackendTest {
    private val bc = defaultX25519Backend(CryptographyProvider.Default)
    private val xdh = XdhX25519Backend(CryptographyProvider.Default)

    @Test
    fun agreesWithRfc7748DiffieHellmanVector() = runTest {
        // RFC 7748 section 6.1.
        val alicePrivate = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val alicePublic = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        val bobPrivate = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val bobPublic = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        val shared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")

        assertContentEquals(alicePublic, bc.publicKey(alicePrivate))
        assertContentEquals(bobPublic, bc.publicKey(bobPrivate))
        assertContentEquals(shared, bc.sharedSecret(alicePrivate, bobPublic))
        assertContentEquals(shared, bc.sharedSecret(bobPrivate, alicePublic))
    }

    @Test
    fun derivesTheSamePublicKeyAsTheXdhBackend() = runTest {
        // An identity persisted by an earlier release was generated through
        // XDH. It must keep its client id under BouncyCastle, or every device
        // that updates loses its pairing.
        repeat(8) {
            val pair = xdh.generateKeyPair()
            assertContentEquals(pair.publicKey, bc.publicKey(pair.privateKey))
        }
    }

    @Test
    fun agreesWithTheXdhBackendOnASharedSecret() = runTest {
        val local = bc.generateKeyPair()
        val remote = xdh.generateKeyPair()

        assertContentEquals(
            xdh.sharedSecret(remote.privateKey, local.publicKey),
            bc.sharedSecret(local.privateKey, remote.publicKey),
        )
    }

    @Test
    fun generatesClampedDistinctKeyPairs() = runTest {
        val first = bc.generateKeyPair()
        val second = bc.generateKeyPair()

        assertEquals(32, first.privateKey.size)
        assertEquals(32, first.publicKey.size)
        assertTrue(!first.privateKey.contentEquals(second.privateKey))
        // RFC 7748 clamping: low three bits cleared, bit 255 cleared, bit 254 set.
        assertEquals(0, first.privateKey[0].toInt() and 0b111)
        assertEquals(0x40, first.privateKey[31].toInt() and 0xC0)
    }

    @Test
    fun rejectsASmallOrderPeerKey() = runTest {
        val pair = bc.generateKeyPair()
        // The all-zero u-coordinate is the canonical small-order point; the
        // agreement is all zeros and must not be used as Noise key material.
        val thrown = runCatching { bc.sharedSecret(pair.privateKey, ByteArray(32)) }
        assertTrue(thrown.isFailure)
    }

    private fun hex(value: String) =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
