package io.music_assistant.sendspin.noise.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.XDH

/**
 * The X25519 half of [NoiseCrypto], split out because it is the one primitive
 * whose availability differs per platform: the Android platform JCA registers
 * X25519 only from API 33, while the rest of the suite (ChaCha20-Poly1305,
 * SHA-256, HMAC) is available across the whole supported range.
 *
 * Keys cross this boundary as raw 32-byte arrays in RFC 7748 little-endian
 * form, which is what every backend below encodes to, so an identity written
 * by one backend stays valid under another.
 */
internal interface X25519Backend {
    /** Generates a fresh keypair from a CSPRNG. */
    suspend fun generateKeyPair(): X25519KeyPair

    /** Derives the public key for a raw 32-byte private key. */
    suspend fun publicKey(privateKey: ByteArray): ByteArray

    /** Diffie-Hellman: the raw 32-byte shared secret for a private/public pair. */
    suspend fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray
}

/** The backend for the current platform. */
internal expect fun defaultX25519Backend(provider: CryptographyProvider): X25519Backend

/**
 * [X25519Backend] over cryptography-kotlin's [XDH] algorithm, and through it
 * whichever provider the target registers.
 */
internal class XdhX25519Backend(
    private val provider: CryptographyProvider,
) : X25519Backend {
    private val xdh by lazy { provider.get(XDH) }

    override suspend fun generateKeyPair(): X25519KeyPair {
        val keyPair = xdh.keyPairGenerator(XDH.Curve.X25519).generateKey()
        return X25519KeyPair(
            privateKey = keyPair.privateKey.encodeToByteArray(XDH.PrivateKey.Format.RAW),
            publicKey = keyPair.publicKey.encodeToByteArray(XDH.PublicKey.Format.RAW),
        )
    }

    override suspend fun publicKey(privateKey: ByteArray): ByteArray {
        // The public key is by definition the DH of the private key with the
        // curve's base point (u = 9), per RFC 7748.
        val basePoint = ByteArray(KEY_SIZE).also { it[0] = 9 }
        return sharedSecret(privateKey, basePoint)
    }

    override suspend fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val private = xdh.privateKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArray(XDH.PrivateKey.Format.RAW, privateKey)
        val public = xdh.publicKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArray(XDH.PublicKey.Format.RAW, publicKey)
        return private.sharedSecretGenerator().generateSharedSecretToByteArray(public)
    }

    private companion object {
        const val KEY_SIZE = 32
    }
}
