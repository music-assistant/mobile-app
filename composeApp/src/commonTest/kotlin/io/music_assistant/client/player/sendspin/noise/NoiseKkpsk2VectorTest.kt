package io.music_assistant.client.player.sendspin.noise

import io.music_assistant.client.player.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.client.player.sendspin.noise.crypto.X25519KeyPair
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the cacophony reference vectors for the 25519_ChaChaPoly_SHA256
 * handshake patterns implemented here, asserting byte-exact handshake
 * messages, transport ciphertexts, and the final handshake hash from both the
 * initiator and responder perspectives. Because the vectors run against the
 * real cryptography-kotlin backend, they also prove the platform primitives
 * (X25519, ChaCha20-Poly1305, SHA-256, HMAC) on every test target.
 */
class NoiseKkpsk2VectorTest {
    private class Vector(
        val protocolName: String,
        val initPrologue: ByteArray,
        val initStatic: ByteArray?,
        val initEphemeral: ByteArray,
        val respStatic: ByteArray?,
        val respEphemeral: ByteArray,
        val psk: ByteArray?,
        val handshakeHash: ByteArray,
        val messages: List<Pair<ByteArray, ByteArray>>,
    )

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { i ->
            substring(2 * i, 2 * i + 2).toInt(16).toByte()
        }
    }

    private fun loadVectors(): List<Vector> {
        val root = Json.parseToJsonElement(NoiseTestVectors.json).jsonObject
        return root.getValue("vectors").jsonArray.map { element ->
            val obj = element.jsonObject
            fun hex(key: String): ByteArray? = obj[key]?.jsonPrimitive?.content?.decodeHex()
            Vector(
                protocolName = obj.getValue("protocol_name").jsonPrimitive.content,
                initPrologue = hex("init_prologue") ?: ByteArray(0),
                initStatic = hex("init_static"),
                initEphemeral = hex("init_ephemeral")!!,
                respStatic = hex("resp_static"),
                respEphemeral = hex("resp_ephemeral")!!,
                psk = obj["init_psks"]?.jsonArray?.firstOrNull()
                    ?.jsonPrimitive?.content?.decodeHex(),
                handshakeHash = hex("handshake_hash")!!,
                messages = obj.getValue("messages").jsonArray.map { m ->
                    val msg = m.jsonObject
                    Pair(
                        msg.getValue("payload").jsonPrimitive.content.decodeHex(),
                        msg.getValue("ciphertext").jsonPrimitive.content.decodeHex(),
                    )
                },
            )
        }
    }

    private fun patternFor(name: String): NoisePattern = when (name) {
        "Noise_KK_25519_ChaChaPoly_SHA256" -> NoisePattern.KK
        "Noise_KKpsk2_25519_ChaChaPoly_SHA256" -> NoisePattern.KKPSK2
        "Noise_NNpsk2_25519_ChaChaPoly_SHA256" -> NoisePattern.NNPSK2
        else -> error("unexpected vector protocol: $name")
    }

    private suspend fun keyPairFrom(
        crypto: CryptographyKotlinNoiseCrypto,
        privateKey: ByteArray?,
    ): X25519KeyPair? = privateKey?.let {
        X25519KeyPair(privateKey = it, publicKey = crypto.x25519PublicKey(it))
    }

    private suspend fun runVector(vector: Vector) {
        val crypto = CryptographyKotlinNoiseCrypto()
        val pattern = patternFor(vector.protocolName)
        val initStatic = keyPairFrom(crypto, vector.initStatic)
        val respStatic = keyPairFrom(crypto, vector.respStatic)

        val initiator = HandshakeState.initialize(
            crypto = crypto,
            pattern = pattern,
            role = NoiseRole.INITIATOR,
            prologue = vector.initPrologue,
            localStatic = initStatic,
            remoteStaticPublic = respStatic?.publicKey,
            psk = vector.psk,
            localEphemeral = keyPairFrom(crypto, vector.initEphemeral),
        )
        val responder = HandshakeState.initialize(
            crypto = crypto,
            pattern = pattern,
            role = NoiseRole.RESPONDER,
            prologue = vector.initPrologue,
            localStatic = respStatic,
            remoteStaticPublic = initStatic?.publicKey,
            psk = vector.psk,
            localEphemeral = keyPairFrom(crypto, vector.respEphemeral),
        )

        // Two handshake messages: initiator writes the first, responder the
        // second; each side must produce the vector's exact ciphertext.
        val handshakeMessages = pattern.messages.size
        for (index in 0 until handshakeMessages) {
            val (payload, expectedCiphertext) = vector.messages[index]
            val writer = if (index % 2 == 0) initiator else responder
            val reader = if (index % 2 == 0) responder else initiator
            val written = writer.writeMessage(payload)
            assertContentEquals(
                expectedCiphertext,
                written,
                "${vector.protocolName} handshake message $index",
            )
            assertContentEquals(payload, reader.readMessage(written))
        }

        assertTrue(initiator.isComplete)
        assertTrue(responder.isComplete)
        val initResult = initiator.result!!
        val respResult = responder.result!!
        assertContentEquals(vector.handshakeHash, initResult.handshakeHash)
        assertContentEquals(vector.handshakeHash, respResult.handshakeHash)

        // Remaining messages exercise transport mode, alternating directions
        // starting with the initiator.
        for (index in handshakeMessages until vector.messages.size) {
            val (payload, expectedCiphertext) = vector.messages[index]
            val sender = if (index % 2 == 0) initResult.transport else respResult.transport
            val receiver = if (index % 2 == 0) respResult.transport else initResult.transport
            val encrypted = sender.encrypt(payload)
            assertContentEquals(
                expectedCiphertext,
                encrypted,
                "${vector.protocolName} transport message $index",
            )
            assertContentEquals(payload, receiver.decrypt(encrypted))
        }
    }

    @Test
    fun kkpsk2VectorPasses() = runTest {
        val vector = loadVectors().single {
            it.protocolName == "Noise_KKpsk2_25519_ChaChaPoly_SHA256"
        }
        assertEquals(6, vector.messages.size)
        runVector(vector)
    }

    @Test
    fun kkVectorPasses() = runTest {
        runVector(loadVectors().single { it.protocolName == "Noise_KK_25519_ChaChaPoly_SHA256" })
    }

    @Test
    fun nnpsk2VectorPasses() = runTest {
        runVector(loadVectors().single { it.protocolName == "Noise_NNpsk2_25519_ChaChaPoly_SHA256" })
    }

    @Test
    fun exhaustedNonceFailsInsteadOfWrapping() = runTest {
        val crypto = CryptographyKotlinNoiseCrypto()
        val cipher = CipherState(crypto)
        cipher.initializeKey(ByteArray(32) { 1 })
        cipher.nonce = ULong.MAX_VALUE
        var failed = false
        try {
            cipher.encryptWithAd(ByteArray(0), byteArrayOf(1))
        } catch (_: NoiseException) {
            failed = true
        }
        assertTrue(failed, "the reserved final counter value must error, never wrap")

        cipher.initializeKey(ByteArray(32) { 1 })
        cipher.nonce = ULong.MAX_VALUE
        failed = false
        try {
            cipher.decryptWithAd(ByteArray(0), ByteArray(17))
        } catch (_: NoiseException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun failedHandshakeStateCannotBeReused() = runTest {
        // Fresh keys rather than the shared vector fixture: the JDK provider
        // pools Cipher instances and refuses back-to-back initialization with
        // an identical key+nonce, which re-decrypting the vector's first
        // message across tests would trigger.
        val crypto = CryptographyKotlinNoiseCrypto()
        val initStatic = crypto.generateX25519KeyPair()
        val respStatic = crypto.generateX25519KeyPair()
        val psk = crypto.randomBytes(32)
        val initiator = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = ByteArray(0),
            localStatic = initStatic,
            remoteStaticPublic = respStatic.publicKey,
            psk = psk,
        )
        val responder = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.RESPONDER,
            prologue = ByteArray(0),
            localStatic = respStatic,
            remoteStaticPublic = initStatic.publicKey,
            psk = psk,
        )
        // A corrupted first message fails AEAD and partially mutates the
        // symmetric state; the handshake must refuse any further use.
        val message1 = initiator.writeMessage(ByteArray(0))
        val corrupted = message1.copyOf()
        corrupted[corrupted.size - 1] = (corrupted.last().toInt() xor 1).toByte()
        var failed = false
        try {
            responder.readMessage(corrupted)
        } catch (_: NoiseException) {
            failed = true
        }
        assertTrue(failed)

        var refused = false
        try {
            responder.readMessage(message1)
        } catch (_: IllegalStateException) {
            refused = true
        }
        assertTrue(refused, "a failed handshake state must be discarded, not retried")
    }

    @Test
    fun tamperedTransportCiphertextFailsAuthentication() = runTest {
        val crypto = CryptographyKotlinNoiseCrypto()
        val vector = loadVectors().single {
            it.protocolName == "Noise_KKpsk2_25519_ChaChaPoly_SHA256"
        }
        val initiator = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.INITIATOR,
            prologue = vector.initPrologue,
            localStatic = keyPairFrom(crypto, vector.initStatic),
            remoteStaticPublic = keyPairFrom(crypto, vector.respStatic)?.publicKey,
            psk = vector.psk,
            localEphemeral = keyPairFrom(crypto, vector.initEphemeral),
        )
        val responder = HandshakeState.initialize(
            crypto = crypto,
            pattern = NoisePattern.KKPSK2,
            role = NoiseRole.RESPONDER,
            prologue = vector.initPrologue,
            localStatic = keyPairFrom(crypto, vector.respStatic),
            remoteStaticPublic = keyPairFrom(crypto, vector.initStatic)?.publicKey,
            psk = vector.psk,
            localEphemeral = keyPairFrom(crypto, vector.respEphemeral),
        )
        responder.readMessage(initiator.writeMessage(ByteArray(0)))
        initiator.readMessage(responder.writeMessage(ByteArray(0)))

        val ciphertext = initiator.result!!.transport.encrypt("hello".encodeToByteArray())
        ciphertext[0] = (ciphertext[0].toInt() xor 0x01).toByte()
        var failed = false
        try {
            responder.result!!.transport.decrypt(ciphertext)
        } catch (_: NoiseException) {
            failed = true
        }
        assertTrue(failed, "tampered ciphertext must fail AEAD authentication")
    }
}
