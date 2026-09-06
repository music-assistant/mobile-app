package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair

/**
 * A focused implementation of the Noise Protocol Framework (revision 34)
 * state machines for the `25519_ChaChaPoly_SHA256` suite, covering the
 * handshake patterns Sendspin needs. Production uses `KKpsk2` with the client
 * as Noise responder (the server is always the Noise initiator regardless of
 * who opened the WebSocket); the initiator role and the extra patterns exist
 * for reference-vector and loopback testing.
 *
 * Written greenfield against the spec (https://noiseprotocol.org/noise.html);
 * `sander/noise-kotlin` (MIT) was assessed as a vendoring candidate and served
 * as a design reference for the state-machine split, but it implements neither
 * PSK tokens nor the KK pattern, so no code was reused. Correctness is pinned
 * by the cacophony reference vectors run in the common test suite; do not edit
 * this file without re-running them.
 */

internal const val DH_LEN = 32
internal const val HASH_LEN = 32
internal const val KEY_LEN = 32
internal const val TAG_LEN = 16

/** Raised on any Noise-level failure (AEAD failure, malformed message). */
class NoiseException(message: String) : Exception(message)

enum class NoiseRole { INITIATOR, RESPONDER }

internal enum class Token { E, S, EE, ES, SE, SS, PSK }

/**
 * A handshake pattern: pre-message static keys plus per-message token lists.
 * `hasPsk` marks psk-modified patterns, where processing an `e` token also
 * mixes the ephemeral public key into the chaining key.
 */
internal class NoisePattern(
    val name: String,
    val initiatorPreSharesStatic: Boolean,
    val responderPreSharesStatic: Boolean,
    val messages: List<List<Token>>,
    val hasPsk: Boolean,
) {
    companion object {
        val KK = NoisePattern(
            name = "KK",
            initiatorPreSharesStatic = true,
            responderPreSharesStatic = true,
            messages = listOf(
                listOf(Token.E, Token.ES, Token.SS),
                listOf(Token.E, Token.EE, Token.SE),
            ),
            hasPsk = false,
        )

        val KKPSK2 = NoisePattern(
            name = "KKpsk2",
            initiatorPreSharesStatic = true,
            responderPreSharesStatic = true,
            messages = listOf(
                listOf(Token.E, Token.ES, Token.SS),
                listOf(Token.E, Token.EE, Token.SE, Token.PSK),
            ),
            hasPsk = true,
        )

        val NNPSK2 = NoisePattern(
            name = "NNpsk2",
            initiatorPreSharesStatic = false,
            responderPreSharesStatic = false,
            messages = listOf(
                listOf(Token.E),
                listOf(Token.E, Token.EE, Token.PSK),
            ),
            hasPsk = true,
        )
    }
}

/**
 * Noise CipherState: a ChaCha20-Poly1305 key plus a per-direction message
 * counter. An unkeyed CipherState passes data through unchanged (used before
 * any DH output is mixed in).
 */
class CipherState internal constructor(private val crypto: NoiseCrypto) {
    private var key: ByteArray? = null

    // Internal visibility so tests can drive the counter to its limit.
    internal var nonce: ULong = 0u

    val hasKey: Boolean get() = key != null

    internal fun initializeKey(newKey: ByteArray?) {
        key = newKey
        nonce = 0u
    }

    private fun checkNonceNotExhausted() {
        // The final counter value is reserved: reusing a (key, nonce) pair
        // after wraparound would be catastrophic for ChaCha20-Poly1305, so
        // the session must end first.
        if (nonce == ULong.MAX_VALUE) {
            throw NoiseException("cipher nonce exhausted")
        }
    }

    private fun nonceBytes(): ByteArray {
        // ChaChaPoly nonce per Noise: 4 zero bytes then the counter as
        // little-endian uint64.
        val n = nonce
        val bytes = ByteArray(NONCE_LEN)
        for (i in 0 until COUNTER_LEN) {
            bytes[NONCE_LEN - COUNTER_LEN + i] = ((n shr (Byte.SIZE_BITS * i)) and 0xFFu).toByte()
        }
        return bytes
    }

    private companion object {
        const val NONCE_LEN = 12
        const val COUNTER_LEN = 8
    }

    suspend fun encryptWithAd(associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val k = key ?: return plaintext
        checkNonceNotExhausted()
        val ciphertext = crypto.aeadEncrypt(k, nonceBytes(), associatedData, plaintext)
        nonce++
        return ciphertext
    }

    suspend fun decryptWithAd(associatedData: ByteArray, ciphertext: ByteArray): ByteArray {
        val k = key ?: return ciphertext
        checkNonceNotExhausted()
        val plaintext = crypto.aeadDecrypt(k, nonceBytes(), associatedData, ciphertext)
            ?: throw NoiseException("AEAD authentication failed")
        nonce++
        return plaintext
    }
}

/**
 * Noise SymmetricState: the chaining key and handshake hash, plus an inner
 * CipherState for handshake-phase encryption.
 */
internal class SymmetricState private constructor(
    private val crypto: NoiseCrypto,
    val cipher: CipherState,
) {
    private lateinit var chainingKey: ByteArray
    lateinit var handshakeHash: ByteArray
        private set

    companion object {
        suspend fun initialize(crypto: NoiseCrypto, protocolName: String): SymmetricState {
            val state = SymmetricState(crypto, CipherState(crypto))
            val nameBytes = protocolName.encodeToByteArray()
            state.handshakeHash = if (nameBytes.size <= HASH_LEN) {
                nameBytes.copyOf(HASH_LEN)
            } else {
                crypto.sha256(nameBytes)
            }
            state.chainingKey = state.handshakeHash.copyOf()
            return state
        }
    }

    private suspend fun hkdf(inputKeyMaterial: ByteArray, outputs: Int): List<ByteArray> {
        val tempKey = crypto.hmacSha256(chainingKey, inputKeyMaterial)
        val out1 = crypto.hmacSha256(tempKey, byteArrayOf(0x01))
        if (outputs == 1) return listOf(out1)
        val out2 = crypto.hmacSha256(tempKey, out1 + byteArrayOf(0x02))
        if (outputs == 2) return listOf(out1, out2)
        val out3 = crypto.hmacSha256(tempKey, out2 + byteArrayOf(0x03))
        return listOf(out1, out2, out3)
    }

    suspend fun mixKey(inputKeyMaterial: ByteArray) {
        val (ck, tempK) = hkdf(inputKeyMaterial, 2)
        chainingKey = ck
        cipher.initializeKey(tempK.copyOf(KEY_LEN))
    }

    suspend fun mixHash(data: ByteArray) {
        handshakeHash = crypto.sha256(handshakeHash + data)
    }

    suspend fun mixKeyAndHash(inputKeyMaterial: ByteArray) {
        val outputs = hkdf(inputKeyMaterial, 3)
        chainingKey = outputs[0]
        mixHash(outputs[1])
        cipher.initializeKey(outputs[2].copyOf(KEY_LEN))
    }

    suspend fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val ciphertext = cipher.encryptWithAd(handshakeHash, plaintext)
        mixHash(ciphertext)
        return ciphertext
    }

    suspend fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val plaintext = cipher.decryptWithAd(handshakeHash, ciphertext)
        mixHash(ciphertext)
        return plaintext
    }

    suspend fun split(): Pair<CipherState, CipherState> {
        val (tempK1, tempK2) = hkdf(ByteArray(0), 2)
        val c1 = CipherState(crypto)
        c1.initializeKey(tempK1.copyOf(KEY_LEN))
        val c2 = CipherState(crypto)
        c2.initializeKey(tempK2.copyOf(KEY_LEN))
        return c1 to c2
    }
}

/**
 * The transport-phase key material produced by a completed handshake:
 * one CipherState per direction plus the final handshake hash `h` (used by
 * Sendspin as the prologue of an in-band re-handshake).
 */
class NoiseTransport internal constructor(
    private val sending: CipherState,
    private val receiving: CipherState,
    val handshakeHash: ByteArray,
) {
    suspend fun encrypt(plaintext: ByteArray): ByteArray {
        if (plaintext.size + TAG_LEN > NoiseFraming.MAX_NOISE_MESSAGE) {
            throw NoiseException("transport plaintext too large: ${plaintext.size}")
        }
        return sending.encryptWithAd(ByteArray(0), plaintext)
    }

    suspend fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size > NoiseFraming.MAX_NOISE_MESSAGE) {
            throw NoiseException("transport ciphertext too large: ${ciphertext.size}")
        }
        return receiving.decryptWithAd(ByteArray(0), ciphertext)
    }
}

/** Result of processing the final handshake message. */
class HandshakeResult internal constructor(
    val transport: NoiseTransport,
    val handshakeHash: ByteArray,
)

/**
 * Noise HandshakeState for the two-message interactive patterns above.
 *
 * Drive it by alternating [writeMessage] and [readMessage] according to the
 * role: the initiator writes message 1 and reads message 2; the responder
 * reads message 1 and writes message 2. After the second message, [result]
 * carries the transport CipherStates and the final handshake hash.
 */
class HandshakeState private constructor(
    private val crypto: NoiseCrypto,
    private val pattern: NoisePattern,
    private val role: NoiseRole,
    private val localStatic: X25519KeyPair?,
    private var remoteStaticPublic: ByteArray?,
    private var psk: ByteArray?,
    private var localEphemeral: X25519KeyPair?,
) {
    private lateinit var symmetric: SymmetricState
    private var remoteEphemeralPublic: ByteArray? = null
    private var messageIndex = 0

    // Once any read or write fails, the symmetric state is partially mutated
    // and must never be reused — the whole handshake must be discarded.
    private var poisoned = false

    var result: HandshakeResult? = null
        private set

    val isComplete: Boolean get() = result != null

    /** The running handshake hash `h`. */
    val handshakeHash: ByteArray get() = symmetric.handshakeHash

    companion object {
        internal suspend fun initialize(
            crypto: NoiseCrypto,
            pattern: NoisePattern,
            role: NoiseRole,
            prologue: ByteArray,
            localStatic: X25519KeyPair? = null,
            remoteStaticPublic: ByteArray? = null,
            psk: ByteArray? = null,
            localEphemeral: X25519KeyPair? = null,
        ): HandshakeState {
            // The PSK may be supplied later via providePsk: with the psk2
            // modifier it is first needed only when processing the second
            // message, after the peer has identified it by psk_id.
            require(psk == null || psk.size == KEY_LEN) { "PSK must be $KEY_LEN bytes" }
            val state = HandshakeState(
                crypto,
                pattern,
                role,
                localStatic,
                remoteStaticPublic,
                psk,
                localEphemeral,
            )
            state.symmetric = SymmetricState.initialize(
                crypto,
                "Noise_${pattern.name}_25519_ChaChaPoly_SHA256",
            )
            state.symmetric.mixHash(prologue)
            // Pre-messages: the initiator's static public key first, then the
            // responder's, each hashed (never sent) because both sides know
            // them in advance in K-type patterns.
            if (pattern.initiatorPreSharesStatic) {
                state.symmetric.mixHash(state.staticPublicFor(NoiseRole.INITIATOR))
            }
            if (pattern.responderPreSharesStatic) {
                state.symmetric.mixHash(state.staticPublicFor(NoiseRole.RESPONDER))
            }
            return state
        }

        /**
         * The Sendspin production configuration: `KKpsk2` with the client as
         * responder. The server's public key is the initiator static. The PSK
         * may be null here and supplied later via [providePsk], once the
         * first handshake message has identified it by `psk_id`.
         */
        suspend fun sendspinResponder(
            crypto: NoiseCrypto,
            prologue: ByteArray,
            clientStatic: X25519KeyPair,
            serverStaticPublic: ByteArray,
            psk: ByteArray?,
        ): HandshakeState = initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.RESPONDER,
            prologue = prologue,
            localStatic = clientStatic,
            remoteStaticPublic = serverStaticPublic,
            psk = psk,
        )
    }

    /**
     * Supplies the PSK after handshaking has begun — used by the Sendspin
     * client, which selects the PSK by the `psk_id` carried in the first
     * handshake message's payload before processing the second message.
     */
    fun providePsk(psk: ByteArray) {
        require(psk.size == KEY_LEN) { "PSK must be $KEY_LEN bytes" }
        check(!isComplete) { "handshake already complete" }
        this.psk = psk
    }

    private fun staticPublicFor(owner: NoiseRole): ByteArray =
        if (owner == role) {
            localStatic?.publicKey ?: throw NoiseException("missing local static key")
        } else {
            remoteStaticPublic ?: throw NoiseException("missing remote static key")
        }

    private val writesMessage: Boolean
        get() = (messageIndex % 2 == 0) == (role == NoiseRole.INITIATOR)

    private fun localEphemeralPrivate(): ByteArray =
        localEphemeral?.privateKey ?: throw NoiseException("missing local ephemeral")

    private fun localStaticPrivate(): ByteArray =
        localStatic?.privateKey ?: throw NoiseException("missing local static")

    private fun remoteEphemeral(): ByteArray =
        remoteEphemeralPublic ?: throw NoiseException("missing remote ephemeral")

    private fun remoteStatic(): ByteArray =
        remoteStaticPublic ?: throw NoiseException("missing remote static")

    private suspend fun processDhToken(token: Token) {
        val secret = when (token) {
            Token.EE -> crypto.dh(localEphemeralPrivate(), remoteEphemeral())
            Token.SS -> crypto.dh(localStaticPrivate(), remoteStatic())

            // ES mixes DH(initiator ephemeral, responder static);
            // SE mixes DH(initiator static, responder ephemeral).
            Token.ES -> if (role == NoiseRole.INITIATOR) {
                crypto.dh(localEphemeralPrivate(), remoteStatic())
            } else {
                crypto.dh(localStaticPrivate(), remoteEphemeral())
            }

            Token.SE -> if (role == NoiseRole.INITIATOR) {
                crypto.dh(localStaticPrivate(), remoteEphemeral())
            } else {
                crypto.dh(localEphemeralPrivate(), remoteStatic())
            }

            else -> throw NoiseException("not a DH token: $token")
        }
        symmetric.mixKey(secret)
    }

    /** Writes the next handshake message carrying [payload]. */
    suspend fun writeMessage(payload: ByteArray): ByteArray {
        check(!poisoned) { "handshake state discarded after an earlier failure" }
        check(!isComplete) { "handshake already complete" }
        check(writesMessage) { "not this side's turn to write" }
        return poisonOnFailure {
            val tokens = pattern.messages[messageIndex]
            var out = ByteArray(0)
            for (token in tokens) {
                when (token) {
                    Token.E -> {
                        val e = localEphemeral ?: crypto.generateX25519KeyPair().also {
                            localEphemeral = it
                        }
                        out += e.publicKey
                        symmetric.mixHash(e.publicKey)
                        if (pattern.hasPsk) symmetric.mixKey(e.publicKey)
                    }

                    Token.S -> throw NoiseException("S token unsupported in these patterns")
                    Token.PSK -> symmetric.mixKeyAndHash(
                        psk ?: throw NoiseException("PSK required but not provided"),
                    )

                    else -> processDhToken(token)
                }
            }
            out += symmetric.encryptAndHash(payload)
            advance()
            out
        }
    }

    /** Reads a received handshake message, returning its payload. */
    suspend fun readMessage(message: ByteArray): ByteArray {
        check(!poisoned) { "handshake state discarded after an earlier failure" }
        check(!isComplete) { "handshake already complete" }
        check(!writesMessage) { "not this side's turn to read" }
        return poisonOnFailure {
            val tokens = pattern.messages[messageIndex]
            var offset = 0
            for (token in tokens) {
                when (token) {
                    Token.E -> {
                        if (message.size - offset < DH_LEN) {
                            throw NoiseException("handshake message too short for ephemeral key")
                        }
                        val re = message.copyOfRange(offset, offset + DH_LEN)
                        offset += DH_LEN
                        remoteEphemeralPublic = re
                        symmetric.mixHash(re)
                        if (pattern.hasPsk) symmetric.mixKey(re)
                    }

                    Token.S -> throw NoiseException("S token unsupported in these patterns")
                    Token.PSK -> symmetric.mixKeyAndHash(
                        psk ?: throw NoiseException("PSK required but not provided"),
                    )

                    else -> processDhToken(token)
                }
            }
            val ciphertext = message.copyOfRange(offset, message.size)
            if (symmetric.cipher.hasKey && ciphertext.size < TAG_LEN) {
                throw NoiseException("handshake message too short for AEAD tag")
            }
            val payload = symmetric.decryptAndHash(ciphertext)
            advance()
            payload
        }
    }

    private inline fun <T> poisonOnFailure(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        poisoned = true
        throw e
    }

    private suspend fun advance() {
        messageIndex++
        if (messageIndex == pattern.messages.size) {
            val (c1, c2) = symmetric.split()
            // c1 protects initiator-to-responder traffic, c2 the reverse.
            val transport = if (role == NoiseRole.INITIATOR) {
                NoiseTransport(sending = c1, receiving = c2, handshakeHash = symmetric.handshakeHash)
            } else {
                NoiseTransport(sending = c2, receiving = c1, handshakeHash = symmetric.handshakeHash)
            }
            result = HandshakeResult(transport, symmetric.handshakeHash)
        }
    }
}
