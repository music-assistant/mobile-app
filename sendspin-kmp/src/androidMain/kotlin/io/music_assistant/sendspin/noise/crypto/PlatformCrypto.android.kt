package io.music_assistant.sendspin.noise.crypto

import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.random.CryptographyRandom
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519

/**
 * The Android platform JCA registers an X25519 `KeyPairGenerator` only from
 * API 33, and the `ChaCha20-Poly1305` cipher name only from API 29 (Conscrypt
 * has just `ChaCha20/Poly1305/NoPadding` on API 28). This module supports
 * API 28, so both come from BouncyCastle, on every API level: one code path,
 * which the JVM host tests run as it is.
 *
 * This is the lightweight API, not the `BouncyCastleProvider` JCA bridge: the
 * calls below are direct, so R8 keeps what they reach and strips the rest of
 * the library. The provider bridge registers its algorithms by reflective
 * class name and cannot be shrunk.
 */
internal actual fun platformCrypto(provider: CryptographyProvider): PlatformCrypto =
    PlatformCrypto(x25519 = BouncyCastleX25519Backend, aead = BouncyCastleChaCha20Poly1305)

private object BouncyCastleChaCha20Poly1305 : AeadBackend {
    private const val TAG_BITS = 128
    private val logger = Logger.withTag("Sendspin.NoiseCrypto")

    override suspend fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray =
        process(forEncryption = true, key, nonce, associatedData, plaintext)

    override suspend fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? =
        try {
            process(forEncryption = false, key, nonce, associatedData, ciphertext)
        } catch (e: InvalidCipherTextException) {
            // A tag mismatch, or input shorter than the tag. Noise treats both as a
            // forged message. A wrong key or nonce size still throws: that is a bug.
            logger.d(e) { "AEAD decrypt failed" }
            null
        }

    /** A new cipher for each message: the instance holds the key and nonce state. */
    private fun process(
        forEncryption: Boolean,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(forEncryption, AEADParameters(KeyParameter(key), TAG_BITS, nonce, associatedData))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val written = cipher.processBytes(input, 0, input.size, output, 0)
        cipher.doFinal(output, written)
        return output
    }
}

private object BouncyCastleX25519Backend : X25519Backend {
    override suspend fun generateKeyPair(): X25519KeyPair {
        // CryptographyRandom is the same CSPRNG the rest of NoiseCrypto draws
        // PSKs and nonces from.
        val privateKey = CryptographyRandom.nextBytes(X25519.SCALAR_SIZE)
        X25519.clampPrivateKey(privateKey)
        return X25519KeyPair(privateKey = privateKey, publicKey = publicKey(privateKey))
    }

    override suspend fun publicKey(privateKey: ByteArray): ByteArray =
        ByteArray(X25519.POINT_SIZE).also {
            X25519.generatePublicKey(privateKey, 0, it, 0)
        }

    override suspend fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray =
        ByteArray(X25519.POINT_SIZE).also {
            // False means an all-zero secret: the peer sent a small-order
            // point. RFC 7748 leaves the reaction to the protocol; Noise
            // cannot continue with it, so refuse rather than key off zeros.
            check(X25519.calculateAgreement(privateKey, 0, publicKey, 0, it, 0)) {
                "X25519 agreement produced an all-zero shared secret"
            }
        }
}
