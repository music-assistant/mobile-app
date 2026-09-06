package io.music_assistant.sendspin.noise.crypto

import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.ChaCha20Poly1305
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * [NoiseCrypto] backed by cryptography-kotlin, which delegates to OS-native
 * crypto: the JCA (JDK provider) on Android and CryptoKit on iOS. The
 * provider artifacts are per-target runtime dependencies;
 * `CryptographyProvider.Default` resolves whichever one is registered on the
 * current platform.
 *
 * Android note: X25519 (`XDH`) through the platform JCA arrives with API 33;
 * on older API levels (minSdk is below that) resolution depends on the
 * runtime's registered providers (Conscrypt ships ChaCha20-Poly1305 from
 * API 28). If X25519 is unavailable at runtime the handshake fails and the
 * session surfaces the error; the `cryptography-provider-jdk-bc` BouncyCastle
 * artifact is the documented drop-in fallback should field reports show this.
 */
class CryptographyKotlinNoiseCrypto(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : NoiseCrypto {
    private val logger = Logger.withTag("NoiseCrypto")

    // Resolved once — hashing and DH hit these on the hot path. The AEAD
    // algorithm is deliberately NOT cached: the JDK provider pools Cipher
    // instances per algorithm handle, and a shared pool trips the JDK's
    // key+nonce-reuse guard when one process runs both Noise roles
    // (loopback tests) — encrypt and decrypt legitimately use the same
    // key and nonce there.
    private val xdh by lazy { provider.get(XDH) }
    private val sha256Hasher by lazy { provider.get(SHA256).hasher() }
    private val hmac by lazy { provider.get(HMAC) }

    override suspend fun generateX25519KeyPair(): X25519KeyPair {
        val keyPair = xdh.keyPairGenerator(XDH.Curve.X25519).generateKey()
        return X25519KeyPair(
            privateKey = keyPair.privateKey.encodeToByteArray(XDH.PrivateKey.Format.RAW),
            publicKey = keyPair.publicKey.encodeToByteArray(XDH.PublicKey.Format.RAW),
        )
    }

    override suspend fun x25519PublicKey(privateKey: ByteArray): ByteArray {
        // The X25519 public key is by definition the DH of the private key
        // with the curve's base point (u = 9), per RFC 7748.
        val basePoint = ByteArray(32).also { it[0] = 9 }
        return dh(privateKey, basePoint)
    }

    override suspend fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val private = xdh.privateKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArray(XDH.PrivateKey.Format.RAW, privateKey)
        val public = xdh.publicKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArray(XDH.PublicKey.Format.RAW, publicKey)
        return private.sharedSecretGenerator().generateSharedSecretToByteArray(public)
    }

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val cipherKey = provider.get(ChaCha20Poly1305)
            .keyDecoder()
            .decodeFromByteArray(ChaCha20Poly1305.Key.Format.RAW, key)
        // Explicit caller-managed nonce: Noise supplies its own counter-based
        // nonces, so the auto-IV encrypt() variants (which prepend a random
        // IV) must not be used here.
        return cipherKey.cipher().encryptWithIv(nonce, plaintext, associatedData)
    }

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? {
        val cipherKey = provider.get(ChaCha20Poly1305)
            .keyDecoder()
            .decodeFromByteArray(ChaCha20Poly1305.Key.Format.RAW, key)
        return try {
            cipherKey.cipher().decryptWithIv(nonce, ciphertext, associatedData)
        } catch (e: Exception) {
            // Tag/AAD mismatch surfaces as an exception from the underlying
            // platform crypto; Noise treats it as a nullable auth failure.
            // Logged at debug because this catch is broad: a configuration
            // bug (wrong key/IV size) would otherwise masquerade as a forged
            // message.
            logger.d(e) { "AEAD decrypt failed" }
            null
        }
    }

    override suspend fun sha256(data: ByteArray): ByteArray = sha256Hasher.hash(data)

    override suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val hmacKey = hmac
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
        return hmacKey.signatureGenerator().generateSignature(data)
    }

    override fun randomBytes(count: Int): ByteArray = CryptographyRandom.nextBytes(count)
}
