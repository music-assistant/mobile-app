package io.music_assistant.client.player.sendspin.noise.crypto

/** A raw X25519 keypair: both keys are 32 bytes. */
class X25519KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

/**
 * The crypto primitives the Sendspin Noise implementation is built on
 * (`Noise_KKpsk2_25519_ChaChaPoly_SHA256`): X25519 Diffie-Hellman,
 * ChaCha20-Poly1305 AEAD, and SHA-256/HMAC-SHA-256 (HKDF is derived from HMAC
 * by the protocol layer).
 *
 * All key material crosses this boundary as raw bytes so the backing library
 * is swappable (currently cryptography-kotlin over JDK/CryptoKit; libsodium
 * bindings would be a drop-in alternative) and so tests can drive the Noise
 * state machine with fixed keys from reference vectors.
 */
interface NoiseCrypto {
    /** Generates a fresh X25519 keypair from a CSPRNG. */
    suspend fun generateX25519KeyPair(): X25519KeyPair

    /** Derives the X25519 public key for a raw 32-byte private key. */
    suspend fun x25519PublicKey(privateKey: ByteArray): ByteArray

    /**
     * X25519 Diffie-Hellman: returns the raw 32-byte shared secret between a
     * local private key and a remote public key.
     */
    suspend fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray

    /**
     * ChaCha20-Poly1305 encryption with an explicit 12-byte nonce and
     * associated data. Returns ciphertext with the 16-byte tag appended.
     */
    suspend fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray

    /**
     * ChaCha20-Poly1305 decryption with an explicit 12-byte nonce and
     * associated data. Returns null when authentication fails.
     */
    suspend fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray?

    /** One-shot SHA-256. */
    suspend fun sha256(data: ByteArray): ByteArray

    /** HMAC-SHA-256 with a raw key. */
    suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /** CSPRNG bytes (PSKs, nonces). */
    fun randomBytes(count: Int): ByteArray
}
