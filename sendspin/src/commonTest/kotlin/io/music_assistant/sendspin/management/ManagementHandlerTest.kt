package io.music_assistant.sendspin.management

import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagementHandlerTest {
    private val crypto = CryptographyKotlinNoiseCrypto()

    private class Fixture(
        val handler: ManagementHandler,
        val trustStore: SendspinTrustStore,
    )

    private suspend fun fixture(): Fixture {
        val trustStore = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        return Fixture(ManagementHandler(trustStore), trustStore)
    }

    private suspend fun Fixture.request(
        type: String,
        payloadJson: String? = null,
        managementActive: Boolean = true,
        sessionPsk: ByteArray? = ByteArray(32) { 0x7A },
    ): ManagementHandler.Outcome = handler.handle(
        type = type,
        payload = payloadJson?.let { Json.parseToJsonElement(it).jsonObject },
        managementActive = managementActive,
        sessionPsk = sessionPsk,
    )

    private fun resultOf(outcome: ManagementHandler.Outcome): String =
        parsed(outcome).getValue("payload").jsonObject.getValue("result").jsonPrimitive.content

    private fun dataOf(outcome: ManagementHandler.Outcome): JsonObject? =
        parsed(outcome).getValue("payload").jsonObject["data"]?.jsonObject

    private fun parsed(outcome: ManagementHandler.Outcome): JsonObject =
        Json.parseToJsonElement(outcome.resultJson).jsonObject.also {
            assertEquals("management/result", it.getValue("type").jsonPrimitive.content)
        }

    @Test
    fun allCommandsOutsideManagementSessionArePermissionDenied() = runTest {
        val f = fixture()
        for (type in listOf(
            "management/list-records",
            "management/add-record",
            "management/remove-record",
            "management/get-pairing-config",
            "management/set-pairing-config",
            "management/open-pairing-window",
        )) {
            val outcome = f.request(type, managementActive = false)
            assertEquals("permission_denied", resultOf(outcome), type)
            assertNull(dataOf(outcome), "$type carries no data on denial")
        }
    }

    @Test
    fun listRecordsReturnsPskIdKindAndUsedTransitions() = runTest {
        val f = fixture()
        val storedPsk = ByteArray(32) { 1 }
        f.trustStore.recordLongTermPsk(storedPsk, "srv-1")

        var records = dataOf(f.request("management/list-records"))!!
            .getValue("records").jsonArray.map { it.jsonObject }
        assertEquals(2, records.size, "pre-provisioned shared record + stored record")
        val stored = records.single { it.containsKey("server_id") }
        assertEquals("srv-1", stored.getValue("server_id").jsonPrimitive.content)
        assertEquals(f.trustStore.pskIdOf(storedPsk), stored.getValue("psk_id").jsonPrimitive.content)
        assertFalse(stored.getValue("used").jsonPrimitive.boolean)
        val shared = records.single { !it.containsKey("server_id") }
        assertFalse(shared.getValue("used").jsonPrimitive.boolean)

        f.trustStore.markRecordUsed(storedPsk)
        records = dataOf(f.request("management/list-records"))!!
            .getValue("records").jsonArray.map { it.jsonObject }
        assertTrue(
            records.single { it.containsKey("server_id") }.getValue("used").jsonPrimitive.boolean,
        )
    }

    @Test
    fun addRecordStoredAndShared() = runTest {
        val f = fixture()
        val psk = ByteArray(32) { 2 }
        val outcome = f.request(
            "management/add-record",
            """{"psk":"${SendspinBase64.encode(psk)}","server_id":"srv-2"}""",
        )
        assertEquals("ok", resultOf(outcome))
        assertContentEquals(psk, f.trustStore.records().single { it.serverId == "srv-2" }.psk)

        val sharedPsk = ByteArray(32) { 3 }
        assertEquals(
            "ok",
            resultOf(f.request("management/add-record", """{"psk":"${SendspinBase64.encode(sharedPsk)}"}""")),
        )
        assertEquals(2, f.trustStore.records().count { it.serverId == null })
    }

    @Test
    fun addRecordRejectsCollisionsAcrossAllCategories() = runTest {
        val f = fixture()
        // Sentinel PSK.
        assertEquals(
            "already_exists",
            resultOf(
                f.request(
                    "management/add-record",
                    """{"psk":"${SendspinBase64.encode(SendspinPsk.SENTINEL_PSK)}"}""",
                ),
            ),
        )
        // The device's Pairing PSK.
        assertEquals(
            "already_exists",
            resultOf(
                f.request(
                    "management/add-record",
                    """{"psk":"${SendspinBase64.encode(f.trustStore.pairingPsk)}"}""",
                ),
            ),
        )
        // An existing record.
        val psk = ByteArray(32) { 4 }
        f.trustStore.recordLongTermPsk(psk, "srv")
        assertEquals(
            "already_exists",
            resultOf(f.request("management/add-record", """{"psk":"${SendspinBase64.encode(psk)}"}""")),
        )
    }

    @Test
    fun addRecordRejectsMalformedPsk() = runTest {
        val f = fixture()
        assertEquals("invalid", resultOf(f.request("management/add-record", """{}""")))
        assertEquals("invalid", resultOf(f.request("management/add-record", """{"psk":"tooshort"}""")))
        assertEquals("invalid", resultOf(f.request("management/add-record", """{"psk":"!!!not-base64url!!!"}""")))
    }

    @Test
    fun removeRecordOutcomesAndOwnRecordGoodbye() = runTest {
        val f = fixture()
        val psk = ByteArray(32) { 5 }
        f.trustStore.recordLongTermPsk(psk, "srv-5")
        val pskId = f.trustStore.pskIdOf(psk)

        assertEquals("invalid", resultOf(f.request("management/remove-record", """{}""")))
        assertEquals(
            "not_found",
            resultOf(f.request("management/remove-record", """{"psk_id":"missing-id"}""")),
        )

        // Removing the requester's own record: ok, then goodbye unauthorized.
        val own = f.request("management/remove-record", """{"psk_id":"$pskId"}""", sessionPsk = psk)
        assertEquals("ok", resultOf(own))
        assertTrue(own.closeUnauthorizedAfterResponse)
        assertTrue(f.trustStore.records().none { it.serverId == "srv-5" })
    }

    @Test
    fun removeRecordRefusesRecordModeReference() = runTest {
        val f = fixture()
        val sharedPskId = f.trustStore.recordModePskId()!!
        val outcome = f.request("management/remove-record", """{"psk_id":"$sharedPskId"}""")
        assertEquals("invalid", resultOf(outcome), "the record-mode target cannot be removed")
        assertEquals(1, f.trustStore.records().count { it.serverId == null })
    }

    @Test
    fun getPairingConfigOmitsPinMethodsAndSecrets() = runTest {
        val f = fixture()
        val data = dataOf(f.request("management/get-pairing-config"))!!
        assertTrue(data.getValue("pairing_psk").jsonObject.getValue("enabled").jsonPrimitive.boolean)
        assertFalse(data.getValue("pairing_psk").jsonObject.containsKey("psk"))
        assertFalse(data.containsKey("static_pin"), "unimplemented PIN methods are absent")
        assertFalse(data.containsKey("dynamic_pin"))
        assertEquals(
            f.trustStore.recordModePskId(),
            data.getValue("record_mode").jsonObject.getValue("psk_id").jsonPrimitive.content,
        )
        assertFalse(
            data.getValue("unpaired_access").jsonObject.getValue("enabled").jsonPrimitive.boolean,
        )
    }

    @Test
    fun setPairingConfigAppliesPatchPreservingAbsentFields() = runTest {
        val f = fixture()
        assertEquals(
            "ok",
            resultOf(f.request("management/set-pairing-config", """{"unpaired_access":{"enabled":true}}""")),
        )
        assertTrue(f.trustStore.unpairedAccessEnabled)
        assertTrue(f.trustStore.pairingPskEnabled, "absent fields stay unchanged")

        assertEquals(
            "ok",
            resultOf(f.request("management/set-pairing-config", """{"pairing_psk":{"enabled":false}}""")),
        )
        assertFalse(f.trustStore.pairingPskEnabled)
        assertTrue(f.trustStore.unpairedAccessEnabled, "earlier patch survives")
    }

    @Test
    fun setPairingConfigRotatesPairingPskAndUpdatesCandidatesImmediately() = runTest {
        val f = fixture()
        val newPsk = ByteArray(32) { 6 }
        assertEquals(
            "ok",
            resultOf(
                f.request(
                    "management/set-pairing-config",
                    """{"pairing_psk":{"psk":"${SendspinBase64.encode(newPsk)}"}}""",
                ),
            ),
        )
        assertContentEquals(newPsk, f.trustStore.pairingPsk)
        // The live candidate set reflects the rotation immediately.
        assertContentEquals(
            newPsk,
            f.trustStore.pskCandidates().single { it.category == PskCategory.PAIRING }.psk,
        )
    }

    @Test
    fun addRecordForSameServerAppendsWithoutReplacing() = runTest {
        val f = fixture()
        val firstPsk = ByteArray(32) { 11 }
        val secondPsk = ByteArray(32) { 12 }
        assertEquals(
            "ok",
            resultOf(
                f.request(
                    "management/add-record",
                    """{"psk":"${SendspinBase64.encode(firstPsk)}","server_id":"srv-x"}""",
                ),
            ),
        )
        assertEquals(
            "ok",
            resultOf(
                f.request(
                    "management/add-record",
                    """{"psk":"${SendspinBase64.encode(secondPsk)}","server_id":"srv-x"}""",
                ),
            ),
        )
        assertEquals(
            2,
            f.trustStore.records().count { it.serverId == "srv-x" },
            "add-record's only conflict rule is the psk_id namespace",
        )
    }

    @Test
    fun setPairingConfigRejectsMalformedBooleanAsInvalid() = runTest {
        val f = fixture()
        assertEquals(
            "invalid",
            resultOf(
                f.request("management/set-pairing-config", """{"pairing_psk":{"enabled":"yes"}}"""),
            ),
            "a present-but-malformed field is invalid, not an absent patch field",
        )
        assertEquals(
            "invalid",
            resultOf(
                f.request("management/set-pairing-config", """{"unpaired_access":{"enabled":1.5}}"""),
            ),
        )
        assertTrue(f.trustStore.pairingPskEnabled, "rejected patches change nothing")
    }

    @Test
    fun setPairingConfigRejectsUnimplementedMethodsAndBadValues() = runTest {
        val f = fixture()
        assertEquals(
            "invalid",
            resultOf(f.request("management/set-pairing-config", """{"static_pin":{"enabled":true}}""")),
        )
        assertEquals(
            "invalid",
            resultOf(f.request("management/set-pairing-config", """{"dynamic_pin":{"enabled":true}}""")),
        )
        assertEquals(
            "invalid",
            resultOf(f.request("management/set-pairing-config", """{"pairing_psk":{"psk":"short"}}""")),
        )
        assertTrue(f.trustStore.pairingPskEnabled, "rejected patches change nothing")
    }

    @Test
    fun setPairingConfigRejectsPairingPskCollidingWithOtherCategory() = runTest {
        val f = fixture()
        val recordPsk = ByteArray(32) { 7 }
        f.trustStore.recordLongTermPsk(recordPsk, "srv")
        assertEquals(
            "already_exists",
            resultOf(
                f.request(
                    "management/set-pairing-config",
                    """{"pairing_psk":{"psk":"${SendspinBase64.encode(recordPsk)}"}}""",
                ),
            ),
        )
        assertEquals(
            "already_exists",
            resultOf(
                f.request(
                    "management/set-pairing-config",
                    """{"pairing_psk":{"psk":"${SendspinBase64.encode(SendspinPsk.SENTINEL_PSK)}"}}""",
                ),
            ),
        )
    }

    @Test
    fun recordModeMustReferenceASharedRecord() = runTest {
        val f = fixture()
        val storedPsk = ByteArray(32) { 8 }
        f.trustStore.recordLongTermPsk(storedPsk, "srv")
        val storedId = f.trustStore.pskIdOf(storedPsk)
        assertEquals(
            "invalid",
            resultOf(
                f.request("management/set-pairing-config", """{"record_mode":{"psk_id":"$storedId"}}"""),
            ),
            "a stored-pubkey record cannot back record mode",
        )
        assertEquals(
            "invalid",
            resultOf(
                f.request("management/set-pairing-config", """{"record_mode":{"psk_id":"missing"}}"""),
            ),
        )

        // A newly added shared record is a valid target.
        val sharedPsk = ByteArray(32) { 9 }
        f.trustStore.addSharedRecord(sharedPsk)
        val sharedId = f.trustStore.pskIdOf(sharedPsk)
        assertEquals(
            "ok",
            resultOf(
                f.request("management/set-pairing-config", """{"record_mode":{"psk_id":"$sharedId"}}"""),
            ),
        )
        assertEquals(sharedId, f.trustStore.recordModePskId())
    }

    @Test
    fun openPairingWindowIsInvalidWithoutPinMethods() = runTest {
        val f = fixture()
        assertEquals("invalid", resultOf(f.request("management/open-pairing-window")))
    }

    @Test
    fun unknownManagementCommandIsInvalid() = runTest {
        val f = fixture()
        assertEquals("invalid", resultOf(f.request("management/frobnicate")))
    }
}
