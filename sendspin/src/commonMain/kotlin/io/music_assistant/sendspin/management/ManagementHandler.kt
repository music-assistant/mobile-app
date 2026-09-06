package io.music_assistant.sendspin.management

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.wire.SendspinJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * The management role: record/pairing-config administration by a paired server.
 * One `management/result` per request; one-in-flight is structural (the session
 * processes inbound messages sequentially). Payloads are never logged —
 * `add-record` and `set-pairing-config` carry raw secrets.
 */
class ManagementHandler(
    private val trustStore: SendspinTrustStore,
) {
    private val logger = Logger.withTag("ManagementHandler")

    /** The reply to send, plus whether the connection must close afterwards. */
    class Outcome(
        val resultJson: String,
        /** The requester removed its own record: respond, then goodbye `unauthorized`. */
        val closeUnauthorizedAfterResponse: Boolean = false,
    )

    /**
     * @param managementActive `'management'` is active on a long-term-PSK session.
     * @param sessionPsk matched PSK, to detect removal of the requester's own record.
     */
    suspend fun handle(
        type: String,
        payload: JsonObject?,
        managementActive: Boolean,
        sessionPsk: ByteArray?,
    ): Outcome {
        logger.i { "Management request: $type" }
        if (!managementActive) {
            return Outcome(resultJson(RESULT_PERMISSION_DENIED))
        }
        return when (type) {
            "management/list-records" -> listRecords()
            "management/add-record" -> addRecord(payload)
            "management/remove-record" -> removeRecord(payload, sessionPsk)
            "management/get-pairing-config" -> getPairingConfig()
            "management/set-pairing-config" -> setPairingConfig(payload)
            "management/open-pairing-window" ->
                // No PIN method is implemented, so none can be enabled.
                Outcome(resultJson(RESULT_INVALID))

            else -> Outcome(resultJson(RESULT_INVALID))
        }
    }

    private suspend fun listRecords(): Outcome {
        val records = trustStore.records().map { record ->
            buildJsonObject {
                put("psk_id", JsonPrimitive(trustStore.pskIdOf(record.psk)))
                record.serverId?.let { put("server_id", JsonPrimitive(it)) }
                put("used", JsonPrimitive(record.used))
            }
        }
        val data = buildJsonObject { put("records", JsonArray(records)) }
        return Outcome(resultJson(RESULT_OK, data))
    }

    private suspend fun addRecord(payload: JsonObject?): Outcome {
        val pskText = (payload?.get("psk") as? JsonPrimitive)?.contentOrNull
            ?: return Outcome(resultJson(RESULT_INVALID))
        val psk = SendspinBase64.decodeOrNull(pskText)?.takeIf { it.size == PSK_SIZE }
            ?: return Outcome(resultJson(RESULT_INVALID))
        val serverId = (payload["server_id"] as? JsonPrimitive)?.contentOrNull

        if (collidesWithKnownPskId(trustStore.pskIdOf(psk))) {
            return Outcome(resultJson(RESULT_ALREADY_EXISTS))
        }
        if (serverId != null) {
            trustStore.addStoredRecord(psk, serverId)
        } else {
            trustStore.addSharedRecord(psk)
        }
        return Outcome(resultJson(RESULT_OK))
    }

    private suspend fun removeRecord(payload: JsonObject?, sessionPsk: ByteArray?): Outcome {
        val pskId = (payload?.get("psk_id") as? JsonPrimitive)?.contentOrNull
            ?: return Outcome(resultJson(RESULT_INVALID))
        // A record still referenced by record_mode cannot be removed.
        if (pskId == trustStore.recordModePskId()) {
            return Outcome(resultJson(RESULT_INVALID))
        }
        val record = trustStore.records().firstOrNull { trustStore.pskIdOf(it.psk) == pskId }
            ?: return Outcome(resultJson(RESULT_NOT_FOUND))

        trustStore.removeRecord(record.psk)
        val removedOwnRecord = sessionPsk != null && record.psk.contentEquals(sessionPsk)
        return Outcome(
            resultJson(RESULT_OK),
            closeUnauthorizedAfterResponse = removedOwnRecord,
        )
    }

    private suspend fun getPairingConfig(): Outcome {
        // PIN-method objects are absent: this client implements neither.
        // Configured secrets are never returned.
        val data = buildJsonObject {
            put(
                "pairing_psk",
                buildJsonObject { put("enabled", JsonPrimitive(trustStore.pairingPskEnabled)) },
            )
            put(
                "record_mode",
                buildJsonObject {
                    trustStore.recordModePskId()?.let { put("psk_id", JsonPrimitive(it)) }
                },
            )
            put(
                "unpaired_access",
                buildJsonObject {
                    put("enabled", JsonPrimitive(trustStore.unpairedAccessEnabled))
                },
            )
        }
        return Outcome(resultJson(RESULT_OK, data))
    }

    /** Marks a present-but-malformed patch field (distinct from an absent one). */
    private class MalformedPatchField : Exception()

    /** Null when absent (keeps stored value); throws when present but malformed. */
    private fun JsonObject.optionalBooleanField(key: String): Boolean? {
        val element = this[key] ?: return null
        return (element as? JsonPrimitive)?.booleanOrNull ?: throw MalformedPatchField()
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private suspend fun setPairingConfig(payload: JsonObject?): Outcome {
        if (payload == null) return Outcome(resultJson(RESULT_INVALID))

        // Fields on methods this client does not implement are invalid.
        if (payload.containsKey("static_pin") || payload.containsKey("dynamic_pin")) {
            return Outcome(resultJson(RESULT_INVALID))
        }

        // Validate the whole patch before writing anything, so a rejected
        // request leaves configuration untouched.
        val newPairingEnabled: Boolean?
        val newUnpairedEnabled: Boolean?
        val pairingPskPatch = payload["pairing_psk"] as? JsonObject
        try {
            newPairingEnabled = pairingPskPatch?.optionalBooleanField("enabled")
            newUnpairedEnabled = (payload["unpaired_access"] as? JsonObject)
                ?.optionalBooleanField("enabled")
        } catch (_: MalformedPatchField) {
            return Outcome(resultJson(RESULT_INVALID))
        }
        val newPairingPsk = (pairingPskPatch?.get("psk") as? JsonPrimitive)?.contentOrNull?.let {
            SendspinBase64.decodeOrNull(it)?.takeIf { psk -> psk.size == PSK_SIZE }
                ?: return Outcome(resultJson(RESULT_INVALID))
        }
        if (newPairingPsk != null &&
            collidesWithOtherCategory(trustStore.pskIdOf(newPairingPsk))
        ) {
            return Outcome(resultJson(RESULT_ALREADY_EXISTS))
        }

        val recordModePatch = payload["record_mode"] as? JsonObject
        val newRecordModePskId = (recordModePatch?.get("psk_id") as? JsonPrimitive)?.contentOrNull
        if (recordModePatch != null && newRecordModePskId == null) {
            return Outcome(resultJson(RESULT_INVALID))
        }
        if (newRecordModePskId != null) {
            // record_mode must reference an existing shared-PSK record.
            val target = trustStore.records()
                .firstOrNull { trustStore.pskIdOf(it.psk) == newRecordModePskId }
            if (target == null || target.serverId != null) {
                return Outcome(resultJson(RESULT_INVALID))
            }
        }

        // One commit: a crash cannot persist half of the request.
        trustStore.applyPairingConfigPatch(
            pairingPsk = newPairingPsk,
            pairingPskEnabled = newPairingEnabled,
            recordModePskId = newRecordModePskId,
            unpairedAccessEnabled = newUnpairedEnabled,
        )
        return Outcome(resultJson(RESULT_OK))
    }

    /** psk_id collision across all candidate categories (one namespace). */
    private suspend fun collidesWithKnownPskId(pskId: String): Boolean =
        pskId == SendspinPsk.SENTINEL_PSK_ID ||
            pskId == trustStore.pskIdOf(trustStore.pairingPsk) ||
            trustStore.records().any { trustStore.pskIdOf(it.psk) == pskId }

    /** Collision for a new Pairing PSK: any *other* category's psk_id. */
    private suspend fun collidesWithOtherCategory(pskId: String): Boolean =
        pskId == SendspinPsk.SENTINEL_PSK_ID ||
            trustStore.records().any { trustStore.pskIdOf(it.psk) == pskId }

    private fun resultJson(result: String, data: JsonObject? = null): String =
        SendspinJson.encodeToString(
            buildJsonObject {
                put("type", JsonPrimitive("management/result"))
                put(
                    "payload",
                    buildJsonObject {
                        put("result", JsonPrimitive(result))
                        if (data != null) put("data", data)
                    },
                )
            },
        )

    private companion object {
        const val PSK_SIZE = 32
        const val RESULT_OK = "ok"
        const val RESULT_PERMISSION_DENIED = "permission_denied"
        const val RESULT_ALREADY_EXISTS = "already_exists"
        const val RESULT_INVALID = "invalid"
        const val RESULT_NOT_FOUND = "not_found"
    }
}
