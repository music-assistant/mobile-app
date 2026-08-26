package io.music_assistant.client.player.sendspin.identity

import io.music_assistant.client.player.sendspin.noise.PskCandidate
import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.noise.SendspinBase64
import io.music_assistant.client.player.sendspin.noise.SendspinPsk
import io.music_assistant.client.player.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.client.player.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.client.player.sendspin.pairing.PairingToken
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.updateAndGet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A persisted long-term PSK record, as exposed to management. */
class TrustRecordView(
    val psk: ByteArray,
    /** Bound server for stored-pubkey records; null for shared-PSK records. */
    val serverId: String?,
    /** True once the record has authenticated at least one session. */
    val used: Boolean,
)

/**
 * Persistent trust state: identity keypair, Pairing PSK, long-term records,
 * config flags, and the live PSK-candidate set. Persisted as two entries: the
 * identity (written only on regeneration) and one versioned trust blob rewritten
 * whole per serialized mutation, bound to the identity public key — a
 * regenerated identity resets the blob with it.
 */
class SendspinTrustStore private constructor(
    private val keyStore: SendspinKeyStore,
    private val crypto: NoiseCrypto,
    val identity: SendspinIdentity,
    initialState: TrustBlob,
) {
    private val state = atomic(initialState)

    val clientId: String get() = identity.clientId

    /** Raw per-device Pairing PSK (32 bytes). */
    val pairingPsk: ByteArray get() = SendspinBase64.decode(state.value.pairingPsk)

    val pairingPskEnabled: Boolean get() = state.value.pairingPskEnabled

    val unpairedAccessEnabled: Boolean get() = state.value.unpairedAccessEnabled

    /** The out-of-band pairing token for this device. */
    fun pairingToken(): String = PairingToken.mint(identity.keyPair.publicKey, pairingPsk)

    /** Derives the `psk_id` of [psk] (management identifies records by it). */
    suspend fun pskIdOf(psk: ByteArray): String = SendspinPsk.pskId(crypto, psk)

    /** Record-distribution shared record; defaults to the pre-provisioned one. */
    suspend fun recordModePskId(): String? {
        state.value.recordModePskId?.let { return it }
        val defaultShared = state.value.records.firstOrNull { it.serverId == null }
            ?: return null
        return pskIdOf(SendspinBase64.decode(defaultShared.psk))
    }

    fun setRecordModePskId(pskId: String) {
        mutate { it.copy(recordModePskId = pskId) }
    }

    /** Live PSK-candidate set, computed from current state on every call so
     *  config mutations are visible to in-flight lookups immediately. */
    fun pskCandidates(): List<PskCandidate> {
        val snapshot = state.value
        return buildList {
            add(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL))
            if (snapshot.pairingPskEnabled) {
                add(PskCandidate(SendspinBase64.decode(snapshot.pairingPsk), PskCategory.PAIRING))
            }
            snapshot.records.forEach { record ->
                add(
                    PskCandidate(
                        psk = SendspinBase64.decode(record.psk),
                        category = if (record.serverId != null) {
                            PskCategory.LONG_TERM_STORED
                        } else {
                            PskCategory.LONG_TERM_SHARED
                        },
                        serverId = record.serverId,
                    ),
                )
            }
        }
    }

    fun records(): List<TrustRecordView> = state.value.records.map {
        TrustRecordView(
            psk = SendspinBase64.decode(it.psk),
            serverId = it.serverId,
            used = it.used,
        )
    }

    /** Persists a completed pairing's record; replaces any for the same server. */
    fun recordLongTermPsk(psk: ByteArray, serverId: String) {
        mutate { blob ->
            blob.copy(
                records = blob.records.filterNot { it.serverId == serverId } +
                    TrustRecord(psk = SendspinBase64.encode(psk), serverId = serverId),
            )
        }
    }

    /** Appends without replacing (management add-record; psk_id conflicts are the caller's). */
    fun addStoredRecord(psk: ByteArray, serverId: String) {
        mutate { blob ->
            blob.copy(
                records = blob.records +
                    TrustRecord(psk = SendspinBase64.encode(psk), serverId = serverId),
            )
        }
    }

    /** One-commit config patch; null fields keep their stored values. */
    fun applyPairingConfigPatch(
        pairingPsk: ByteArray? = null,
        pairingPskEnabled: Boolean? = null,
        recordModePskId: String? = null,
        unpairedAccessEnabled: Boolean? = null,
    ) {
        require(pairingPsk == null || pairingPsk.size == PSK_SIZE) {
            "pairing PSK must be $PSK_SIZE bytes"
        }
        mutate { blob ->
            blob.copy(
                pairingPsk = pairingPsk?.let { SendspinBase64.encode(it) } ?: blob.pairingPsk,
                pairingPskEnabled = pairingPskEnabled ?: blob.pairingPskEnabled,
                recordModePskId = recordModePskId ?: blob.recordModePskId,
                unpairedAccessEnabled = unpairedAccessEnabled ?: blob.unpairedAccessEnabled,
            )
        }
    }

    /** Adds a shared-PSK record (no bound server). */
    fun addSharedRecord(psk: ByteArray) {
        mutate { blob ->
            blob.copy(
                records = blob.records +
                    TrustRecord(psk = SendspinBase64.encode(psk), serverId = null),
            )
        }
    }

    /** Removes records matching [psk]; returns true when any was removed. */
    fun removeRecord(psk: ByteArray): Boolean {
        val encoded = SendspinBase64.encode(psk)
        var removed = false
        mutate { blob ->
            val remaining = blob.records.filterNot { it.psk == encoded }
            removed = remaining.size != blob.records.size
            blob.copy(records = remaining)
        }
        return removed
    }

    /** Marks the record matching [psk] as having authenticated a session. */
    fun markRecordUsed(psk: ByteArray) {
        val encoded = SendspinBase64.encode(psk)
        mutate { blob ->
            blob.copy(
                records = blob.records.map {
                    if (it.psk == encoded) it.copy(used = true) else it
                },
            )
        }
    }

    fun setPairingPskEnabled(enabled: Boolean) {
        mutate { it.copy(pairingPskEnabled = enabled) }
    }

    fun setUnpairedAccessEnabled(enabled: Boolean) {
        mutate { it.copy(unpairedAccessEnabled = enabled) }
    }

    /** Rotates the Pairing PSK (management-driven; never rotated locally). */
    fun setPairingPsk(psk: ByteArray) {
        require(psk.size == PSK_SIZE) { "pairing PSK must be $PSK_SIZE bytes" }
        mutate { it.copy(pairingPsk = SendspinBase64.encode(psk)) }
    }

    private val persistLock = SynchronizedObject()

    private fun mutate(transform: (TrustBlob) -> TrustBlob) {
        // The lock keeps persist order identical to mutation order; otherwise two
        // writes could land on disk reversed and resurrect old state after a restart.
        synchronized(persistLock) {
            val newState = state.updateAndGet(transform)
            keyStore.write(TRUST_KEY, json.encodeToString(newState).encodeToByteArray())
        }
    }

    companion object {
        private const val IDENTITY_KEY = "sendspin.identity"
        private const val TRUST_KEY = "sendspin.trust"
        private const val KEY_SIZE = 32
        private const val PSK_SIZE = 32

        private val json = Json { ignoreUnknownKeys = true }

        /** Loads persisted state, regenerating cleanly on missing/corrupt storage. */
        suspend fun load(keyStore: SendspinKeyStore, crypto: NoiseCrypto): SendspinTrustStore {
            val identity = readIdentity(keyStore, crypto) ?: run {
                val keyPair = crypto.generateX25519KeyPair()
                keyStore.write(
                    IDENTITY_KEY,
                    json.encodeToString(
                        IdentityBlob(
                            privateKey = SendspinBase64.encode(keyPair.privateKey),
                            publicKey = SendspinBase64.encode(keyPair.publicKey),
                        ),
                    ).encodeToByteArray(),
                )
                SendspinIdentity(keyPair)
            }

            val stored = readTrust(keyStore)
            val trust = if (stored != null && stored.identityPublicKey == identity.clientId) {
                stored
            } else {
                // Missing, corrupt, or bound to a previous identity: reset.
                val fresh = TrustBlob(
                    identityPublicKey = identity.clientId,
                    pairingPsk = SendspinBase64.encode(crypto.randomBytes(PSK_SIZE)),
                    records = listOf(
                        // Pre-provisioned shared record for record-distribution mode.
                        TrustRecord(
                            psk = SendspinBase64.encode(crypto.randomBytes(PSK_SIZE)),
                            serverId = null,
                        ),
                    ),
                )
                keyStore.write(TRUST_KEY, json.encodeToString(fresh).encodeToByteArray())
                fresh
            }
            return SendspinTrustStore(keyStore, crypto, identity, trust)
        }

        private suspend fun readIdentity(
            keyStore: SendspinKeyStore,
            crypto: NoiseCrypto,
        ): SendspinIdentity? {
            val bytes = keyStore.read(IDENTITY_KEY) ?: return null
            return try {
                val blob = json.decodeFromString<IdentityBlob>(bytes.decodeToString())
                val privateKey = SendspinBase64.decode(blob.privateKey)
                val publicKey = SendspinBase64.decode(blob.publicKey)
                check(privateKey.size == KEY_SIZE && publicKey.size == KEY_SIZE)
                // A public key that no longer matches its private key is corruption.
                check(crypto.x25519PublicKey(privateKey).contentEquals(publicKey))
                SendspinIdentity(X25519KeyPair(privateKey, publicKey))
            } catch (_: Exception) {
                null
            }
        }

        private fun readTrust(keyStore: SendspinKeyStore): TrustBlob? {
            val bytes = keyStore.read(TRUST_KEY) ?: return null
            return try {
                val blob = json.decodeFromString<TrustBlob>(bytes.decodeToString())
                check(blob.version == TrustBlob.CURRENT_VERSION)
                // A truncated blob is corrupt, not half-loaded.
                check(SendspinBase64.decode(blob.pairingPsk).size == PSK_SIZE)
                blob.records.forEach {
                    check(SendspinBase64.decode(it.psk).size == PSK_SIZE)
                }
                blob
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
internal data class IdentityBlob(
    val version: Int = 1,
    @SerialName("private_key") val privateKey: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
internal data class TrustRecord(
    val psk: String,
    @SerialName("server_id") val serverId: String? = null,
    val used: Boolean = false,
)

@Serializable
internal data class TrustBlob(
    val version: Int = CURRENT_VERSION,
    @SerialName("identity_public_key") val identityPublicKey: String,
    @SerialName("pairing_psk") val pairingPsk: String,
    @SerialName("pairing_psk_enabled") val pairingPskEnabled: Boolean = true,
    @SerialName("unpaired_access_enabled") val unpairedAccessEnabled: Boolean = false,
    /** psk_id of the record-distribution shared record; null = the default. */
    @SerialName("record_mode_psk_id") val recordModePskId: String? = null,
    val records: List<TrustRecord> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
