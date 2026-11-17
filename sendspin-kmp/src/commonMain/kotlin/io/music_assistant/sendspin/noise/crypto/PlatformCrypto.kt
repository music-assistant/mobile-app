package io.music_assistant.sendspin.noise.crypto

import co.touchlab.kermit.Logger
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.ChaCha20Poly1305

/**
 * The primitives of [NoiseCrypto] whose availability differs per platform. The Android
 * platform JCA registers X25519 only from API 33, and registers ChaCha20-Poly1305 under
 * the name `ChaCha20-Poly1305` (the one cryptography-kotlin asks for) only from API 29,
 * while the module supports API 28. SHA-256 and HMAC stay on the platform provider.
 */
internal class PlatformCrypto(val x25519: X25519Backend, val aead: AeadBackend)

/**
 * The backends for the current platform. This is the only `expect` declaration in the
 * module: a pure-common `sendspin-core` split would have to publish the backends behind
 * an opt-in marker. A new platform difference goes through a `SendspinDeps` port.
 */
internal expect fun platformCrypto(provider: CryptographyProvider): PlatformCrypto

/** ChaCha20-Poly1305 with a caller-managed 12-byte nonce; the 16-byte tag follows the ciphertext. */
internal interface AeadBackend {
    suspend fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray

    /** Null when authentication fails. */
    suspend fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray?
}

/**
 * [AeadBackend] over cryptography-kotlin, and through it whichever provider the target
 * registers. The algorithm is deliberately NOT cached: the JDK provider pools Cipher
 * instances per algorithm handle, and a shared pool trips the JDK's key+nonce-reuse guard
 * when one process runs both Noise roles (loopback tests) — encrypt and decrypt
 * legitimately use the same key and nonce there.
 */
internal class CryptographyKotlinAead(private val provider: CryptographyProvider) : AeadBackend {
    private val logger = Logger.withTag("Sendspin.NoiseCrypto")

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray =
        // Explicit caller-managed nonce: Noise supplies its own counter-based
        // nonces, so the auto-IV encrypt() variants (which prepend a random
        // IV) must not be used here.
        cipherKey(key).cipher().encryptWithIv(nonce, plaintext, associatedData)

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? {
        val cipherKey = cipherKey(key)
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

    private suspend fun cipherKey(key: ByteArray) = provider.get(ChaCha20Poly1305)
        .keyDecoder()
        .decodeFromByteArray(ChaCha20Poly1305.Key.Format.RAW, key)
}
