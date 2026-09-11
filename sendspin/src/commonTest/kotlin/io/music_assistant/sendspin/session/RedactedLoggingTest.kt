package io.music_assistant.sendspin.session

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.wire.SendspinJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * No exercised secret reaches the logs: pairing with a fresh long-term PSK and
 * management requests carrying raw PSKs run under a capturing writer, and the
 * capture is searched for every secret's encoded form.
 */
internal class RedactedLoggingTest : NoiseSessionTestHarness() {
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

    private fun type(json: String) = SendspinJson.parseToJsonElement(json).jsonObject

    @Test
    fun pairingAndManagementFlowsNeverLogSecrets() = sessionTest { f ->
        Logger.setLogWriters(writer)
        val server = pairingPreamble(f)

        val finalize = type(server.receiveJson())
        val longTermPskB64 = finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")

        val mgmtPsk = ByteArray(32) { 0x5C }
        server.rehandshake(SendspinBase64.decode(longTermPskB64))
        server.completeHelloExchange()
        server.activate(activities = """["management"]""", activeRoles = "[]")
        server.sendJson(
            """{"type":"management/add-record","payload":""" +
                """{"psk":"${SendspinBase64.encode(mgmtPsk)}","server_id":"other"}}""",
        )
        while (type(server.receiveJson()).getValue("type").jsonPrimitive.content != "management/result") {
            continue
        }

        val configPsk = ByteArray(32) { 0x77 }
        server.sendJson(
            """{"type":"management/set-pairing-config","payload":""" +
                """{"pairing_psk":{"psk":"${SendspinBase64.encode(configPsk)}","enabled":true}}}""",
        )
        while (true) {
            val message = type(server.receiveJson())
            if (message.getValue("type").jsonPrimitive.content == "management/result" &&
                message.getValue("payload").jsonObject.getValue("result").jsonPrimitive.content == "ok"
            ) {
                break
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
