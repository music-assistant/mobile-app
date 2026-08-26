package io.music_assistant.client.player.sendspin.session

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.music_assistant.client.player.sendspin.noise.SendspinBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves no exercised secret reaches the logs: pairing with a fresh long-term
 * PSK, management add-record and set-pairing-config carrying raw PSKs, all run
 * with a capturing log writer, and the capture is then searched for every
 * secret's encoded form (including the identity private key and pairing token,
 * which the exercised handlers had in reach throughout). Protocol handlers log
 * message types and lengths only, never payload JSON.
 */
internal class RedactedLoggingTest : EncryptedSessionTestHarness() {
    private class CapturingWriter : LogWriter() {
        val lines = mutableListOf<String>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            lines.add("$tag: $message ${throwable?.message.orEmpty()}")
        }
    }

    private val writer = CapturingWriter()

    @AfterTest
    fun restoreLoggers() {
        Logger.setLogWriters()
    }

    @Test
    fun pairingAndManagementFlowsNeverLogSecrets() = runRealTime {
        Logger.setLogWriters(writer)

        val f = fixture(this)
        val server = pairingPreamble(f)

        // Complete a pairing: the fresh long-term PSK crosses the session.
        val finalize = json.parseToJsonElement(server.receiveJson()).jsonObject
        val longTermPskB64 = finalize.getValue("payload").jsonObject
            .getValue("long_term_psk").jsonPrimitive.content
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")

        // Management requests carrying raw secrets on a paired session.
        val mgmtPsk = ByteArray(32) { 0x5C }
        server.rehandshake(SendspinBase64.decode(longTermPskB64))
        server.completeHelloExchange()
        server.activate(activities = """["management"]""", activeRoles = "[]")
        server.sendJson(
            """{"type":"management/add-record","payload":""" +
                """{"psk":"${SendspinBase64.encode(mgmtPsk)}","server_id":"other"}}""",
        )
        // Wait (bounded) for the management reply so all handling ran.
        kotlinx.coroutines.withTimeout(10_000) {
            var sawResult = false
            while (!sawResult) {
                val message = Json.parseToJsonElement(server.receiveJson()).jsonObject
                sawResult = message.getValue("type").jsonPrimitive.content == "management/result"
            }
        }

        // set-pairing-config carries a raw replacement Pairing PSK.
        val configPsk = ByteArray(32) { 0x77 }
        server.sendJson(
            """{"type":"management/set-pairing-config","payload":""" +
                """{"pairing_psk":{"psk":"${SendspinBase64.encode(configPsk)}","enabled":true}}}""",
        )
        kotlinx.coroutines.withTimeout(10_000) {
            var sawOk = false
            while (!sawOk) {
                val message = Json.parseToJsonElement(server.receiveJson()).jsonObject
                sawOk = message.getValue("type").jsonPrimitive.content == "management/result" &&
                    message.getValue("payload").jsonObject
                        .getValue("result").jsonPrimitive.content == "ok"
            }
        }

        val allLogs = writer.lines.joinToString("\n")
        assertTrue(writer.lines.isNotEmpty(), "the flow must have produced logs")

        val secrets = mapOf(
            "long-term PSK" to longTermPskB64,
            "pairing PSK" to SendspinBase64.encode(f.trustStore.pairingPsk),
            "identity private key" to SendspinBase64.encode(f.trustStore.identity.keyPair.privateKey),
            "pairing token" to f.trustStore.pairingToken(),
            "management-supplied PSK" to SendspinBase64.encode(mgmtPsk),
            "config-supplied pairing PSK" to SendspinBase64.encode(configPsk),
        )
        for ((name, secret) in secrets) {
            assertFalse(allLogs.contains(secret), "logs must not contain the $name")
        }
    }
}
