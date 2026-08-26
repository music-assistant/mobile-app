package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.model.ClientAuthMessage
import io.music_assistant.client.player.sendspin.model.ClientHelloMessage
import io.music_assistant.client.player.sendspin.model.ClientStateMessage
import io.music_assistant.client.player.sendspin.model.ClientStatePayload
import io.music_assistant.client.player.sendspin.model.ClientTimeMessage
import io.music_assistant.client.player.sendspin.model.ClientTimePayload
import io.music_assistant.client.player.sendspin.model.PlayerStateObject
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exact wire bytes of the legacy (unencrypted) protocol's outbound
 * messages. Older Music Assistant servers must keep seeing a byte-identical
 * client, so any change to these fixtures is a compatibility break and must
 * be deliberate.
 */
class LegacyWireGoldenTest {
    private val config = SendspinConfig(
        clientId = "client-golden",
        deviceName = "Golden Device",
        codecPreference = Codec.FLAC,
        bufferCapacityBytes = 15_000_000,
        serverHost = "example.local",
        serverPort = 8095,
        mainConnectionPort = 8095,
        authToken = "token-golden",
    )

    @Test
    fun authMessageWireBytesAreUnchanged() {
        val expected = """
            {
                "type": "auth",
                "token": "token-golden",
                "client_id": "client-golden"
            }
        """.trimIndent()
        assertEquals(
            expected,
            myJson.encodeToString(
                ClientAuthMessage(token = "token-golden", clientId = "client-golden"),
            ),
        )
    }

    @Test
    fun clientStateWireBytesAreUnchanged() {
        val expected = """
            {
                "type": "client/state",
                "payload": {
                    "player": {
                        "state": "synchronized"
                    },
                    "available": true
                }
            }
        """.trimIndent()
        assertEquals(
            expected,
            myJson.encodeToString(
                ClientStateMessage(
                    payload = ClientStatePayload(
                        player = PlayerStateObject(state = PlayerStateValue.SYNCHRONIZED),
                        available = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun clientTimeWireBytesAreUnchanged() {
        val expected = """
            {
                "type": "client/time",
                "payload": {
                    "client_transmitted": 123456789
                }
            }
        """.trimIndent()
        assertEquals(
            expected,
            myJson.encodeToString(
                ClientTimeMessage(payload = ClientTimePayload(clientTransmitted = 123456789L)),
            ),
        )
    }

    @Test
    fun clientHelloWireShapeIsUnchanged() {
        val capabilities = SendspinCapabilities.buildClientHello(config, Codec.OPUS)
        val actual = myJson.encodeToString(ClientHelloMessage(payload = capabilities))

        // Head and structural fields pinned exactly; the supported_formats list
        // is the 15-entry sample-rate × bit-depth expansion, pinned by its
        // first entry, count, and tail.
        val expectedHead = """
            {
                "type": "client/hello",
                "payload": {
                    "client_id": "client-golden",
                    "name": "Golden Device",
                    "device_info": {
                        "model": "Mobile Application",
                        "model_id": "mobile_app",
                        "manufacturer": "Music Assistant",
                        "manufacturer_id": "music_assistant",
                        "software_version": "1.0.0"
                    },
                    "version": 1,
                    "supported_roles": [
                        "player@v1"
                    ],
                    "player@v1_support": {
                        "supported_formats": [
                            {
                                "codec": "opus",
                                "channels": 2,
                                "sample_rate": 44100,
                                "bit_depth": 16
                            },
        """.trimIndent()
        val expectedTail = """
                        ],
                        "buffer_capacity": 15000000,
                        "supported_commands": []
                    }
                }
            }
        """.trimIndent()

        assertEquals(expectedHead, actual.substring(0, expectedHead.length))
        assertEquals(expectedTail, actual.substring(actual.length - expectedTail.length))
        assertEquals(15, Regex("\"codec\": \"opus\"").findAll(actual).count())
    }
}
