package io.music_assistant.sendspin.noise.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * [NoiseCrypto] backed by cryptography-kotlin, which delegates to OS-native
 * crypto: the JCA (JDK provider) on Android and CryptoKit on iOS. The
 * provider artifacts are per-target runtime dependencies;
 * `CryptographyProvider.Default` resolves whichever one is registered on the
 * current platform.
 *
 * X25519 and ChaCha20-Poly1305 are the exception: the Android platform JCA lacks
 * them on part of the supported range, so they go through [PlatformCrypto] — see
 * [platformCrypto] for the per-target choice. SHA-256 and HMAC stay on the
 * platform provider.
 */
internal class CryptographyKotlinNoiseCrypto(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    private val platform: PlatformCrypto = platformCrypto(provider),
) : NoiseCrypto {
    // Resolved once — hashing hits these on the hot path.
    private val sha256Hasher by lazy { provider.get(SHA256).hasher() }
    private val hmac by lazy { provider.get(HMAC) }

    override suspend fun generateX25519KeyPair(): X25519KeyPair = platform.x25519.generateKeyPair()

    override suspend fun x25519PublicKey(privateKey: ByteArray): ByteArray =
        platform.x25519.publicKey(privateKey)

    override suspend fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray =
        platform.x25519.sharedSecret(privateKey, publicKey)

    override suspend fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = platform.aead.encrypt(key, nonce, associatedData, plaintext)

    override suspend fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? = platform.aead.decrypt(key, nonce, associatedData, ciphertext)

    override suspend fun sha256(data: ByteArray): ByteArray = sha256Hasher.hash(data)

    override suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val hmacKey = hmac
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
        return hmacKey.signatureGenerator().generateSignature(data)
    }

    override fun randomBytes(count: Int): ByteArray = CryptographyRandom.nextBytes(count)
}
