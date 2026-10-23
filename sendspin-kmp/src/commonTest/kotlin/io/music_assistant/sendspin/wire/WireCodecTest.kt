package io.music_assistant.sendspin.wire

import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioFormatSpec
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WireCodecTest {
    @Test
    fun parsesEveryServerMessageType() {
        assertIs<ServerMessage.AuthOk>(WireCodec.parse("""{"type":"auth_ok"}"""))
        assertEquals(
            ServerMessage.Hello("MA"),
            WireCodec.parse("""{"type":"server/hello","payload":{"name":"MA"}}"""),
        )
        val activate = WireCodec.parse(
            """{"type":"server/activate","payload":{"activities":["playback"],"active_roles":["player@v1"]}}""",
        )
        assertEquals(
            ServerMessage.Activate(ServerActivatePayload(listOf("playback"), listOf("player@v1"))),
            activate,
        )
        assertEquals(
            ServerMessage.Time(ServerTimePayload(1, 2, 3)),
            WireCodec.parse(
                """{"type":"server/time","payload":{"client_transmitted":1,"server_received":2,"server_transmitted":3}}""",
            ),
        )
        val state = WireCodec.parse("""{"type":"server/state","payload":{"x":1}}""")
        assertIs<ServerMessage.State>(state)
        assertEquals(JsonPrimitive(1), state.payload?.jsonObject?.get("x"))
        assertEquals(
            ServerMessage.Command(PlayerCommandObject("volume", volume = 42)),
            WireCodec.parse("""{"type":"server/command","payload":{"player":{"command":"volume","volume":42}}}"""),
        )
        assertEquals(
            ServerMessage.StreamStart(StreamStartPlayer("flac", 48000, 2, 16, "aGVhZGVy")),
            WireCodec.parse(
                """{"type":"stream/start","payload":{"player":{"codec":"flac","sample_rate":48000,""" +
                    """"channels":2,"bit_depth":16,"codec_header":"aGVhZGVy"},"artwork":{}}}""",
            ),
        )
        assertIs<ServerMessage.StreamEnd>(WireCodec.parse("""{"type":"stream/end"}"""))
        assertIs<ServerMessage.StreamClear>(WireCodec.parse("""{"type":"stream/clear","payload":{}}"""))
        assertEquals(
            ServerMessage.StreamMetadata(StreamMetadataPayload(title = "T")),
            WireCodec.parse("""{"type":"stream/metadata","payload":{"title":"T"}}"""),
        )
        assertEquals(
            ServerMessage.SessionUpdate(SessionUpdatePayload(playbackState = "playing")),
            WireCodec.parse("""{"type":"session/update","payload":{"playback_state":"playing"}}"""),
        )
        assertEquals(
            ServerMessage.GroupUpdate(GroupUpdatePayload(groupId = "g")),
            WireCodec.parse("""{"type":"group/update","payload":{"group_id":"g"}}"""),
        )
        assertIs<ServerMessage.PairFinalize>(WireCodec.parse("""{"type":"server/pair-finalize"}"""))
        assertEquals(
            ServerMessage.PairAbort("timeout"),
            WireCodec.parse("""{"type":"pair/abort","payload":{"reason":"timeout"}}"""),
        )
        assertIs<ServerMessage.Unpair>(WireCodec.parse("""{"type":"server/unpair"}"""))
        assertEquals(
            ServerMessage.NoiseHandshake("AAEC"),
            WireCodec.parse("""{"type":"noise/handshake","payload":{"data":"AAEC"}}"""),
        )
    }

    @Test
    fun managementKeepsTypeAndRawPayload() {
        val message = WireCodec.parse("""{"type":"management/add-record","payload":{"psk":"x"}}""")
        assertIs<ServerMessage.Management>(message)
        assertEquals("management/add-record", message.type)
        assertEquals(JsonPrimitive("x"), message.payload?.get("psk"))
        val noPayload = WireCodec.parse("""{"type":"management/list-records"}""")
        assertIs<ServerMessage.Management>(noPayload)
        assertNull(noPayload.payload)
    }

    @Test
    fun unknownTypeIsReportedNotThrown() {
        assertEquals(ServerMessage.Unknown("pair/pin"), WireCodec.parse("""{"type":"pair/pin"}"""))
    }

    @Test
    fun malformedInputNeverThrows() {
        assertIs<ServerMessage.BadEnvelope>(WireCodec.parse("not json"))
        assertIs<ServerMessage.BadEnvelope>(WireCodec.parse("""{"payload":{}}"""))
        assertIs<ServerMessage.BadEnvelope>(WireCodec.parse("""{"type":5,"payload":{}}"""))
        assertIs<ServerMessage.Malformed>(WireCodec.parse("""{"type":"server/time","payload":{}}"""))
        assertIs<ServerMessage.BadEnvelope>(WireCodec.parse("""[1,2]"""))
        assertIs<ServerMessage.StreamEnd>(WireCodec.parse("""{"type":"stream/end"}"""))
        assertIs<ServerMessage.BadEnvelope>(WireCodec.parse("""{"type":"stream/end"}""", requirePayload = true))
        assertIs<ServerMessage.BadEnvelope>(
            WireCodec.parse("""{"type":"stream/end","payload":[]}""", requirePayload = true),
        )
    }

    @Test
    fun unknownFieldsAreTolerated() {
        assertEquals(
            ServerMessage.Hello("MA"),
            WireCodec.parse("""{"type":"server/hello","payload":{"name":"MA","future":true},"extra":1}"""),
        )
    }

    @Test
    fun clientMessagesEncodeToCompactWireBytes() {
        assertEquals(
            """{"type":"auth","token":"tok","client_id":"cid"}""",
            WireCodec.encode(ClientAuthMessage(token = "tok", clientId = "cid")),
        )
        assertEquals(
            """{"type":"client/time","payload":{"client_transmitted":123}}""",
            WireCodec.encode(ClientTimeMessage(payload = ClientTimePayload(123))),
        )
        assertEquals(
            """{"type":"client/state","payload":{"player":{"state":"synchronized"},"available":true}}""",
            WireCodec.encode(
                ClientStateMessage(
                    payload = ClientStatePayload(PlayerStateObject.legacy(PlayerStateValue.SYNCHRONIZED), true),
                ),
            ),
        )
        assertEquals(
            """{"type":"client/state","payload":{"player":{"output_delay_ms":120,"required_lead_time_ms":250,""" +
                """"min_buffer_ms":1000,"supported_commands":[]},"available":true}}""",
            WireCodec.encode(
                ClientStateMessage(
                    payload = ClientStatePayload(PlayerStateObject.spec(120, 250, 1000), true),
                ),
            ),
        )
        assertEquals(
            """{"type":"client/pair-init","payload":{"pairing_index":2}}""",
            WireCodec.encode(ClientPairInitMessage(payload = ClientPairInitPayload(2))),
        )
        assertEquals(
            """{"type":"client/goodbye","payload":{"reason":"restart"}}""",
            WireCodec.encode(ClientGoodbyeMessage(payload = GoodbyePayload(GoodbyeReason.Restart.wire))),
        )
    }

    @Test
    fun encryptedClientHelloWireShapeIsFrozen() {
        val hello = EncryptedClientHelloMessage(
            payload = EncryptedClientHelloPayload(
                name = "Phone",
                deviceInfo = EncryptedDeviceInfo(productName = "App", manufacturer = "MA", softwareVersion = "1"),
                trustLevel = "user",
                supportedRoles = listOf(VersionedRole.PLAYER_V1),
                playerV1Support = PlayerSupport(
                    supportedFormats = listOf(WireAudioFormat.from(AudioFormatSpec(AudioCodec.FLAC, 2, 48000, 16))),
                    bufferCapacity = 15_000_000,
                    supportedCommands = listOf(PlayerCommand.VOLUME),
                ),
                supportedPairMethods = pairMethods(WireVersion.LEGACY_9, listOf("psk")),
                unpairedAccess = UnpairedAccess(false),
            ),
        )
        assertEquals(
            """{"type":"client/hello","payload":{"name":"Phone",""" +
                """"device_info":{"product_name":"App","manufacturer":"MA","software_version":"1"},""" +
                """"trust_level":"user","supported_roles":["player@v1"],""" +
                """"player@v1_support":{"supported_formats":[{"codec":"flac","channels":2,"sample_rate":48000,"bit_depth":16}],""" +
                """"buffer_capacity":15000000,"supported_commands":["volume"]},""" +
                """"supported_pair_methods":[{"method":"psk"}],"unpaired_access":{"enabled":false}}}""",
            WireCodec.encode(hello),
        )
    }

    @Test
    fun specClientHelloWireShapeIsFrozen() {
        val hello = EncryptedClientHelloMessage(
            payload = EncryptedClientHelloPayload(
                name = "Phone",
                deviceInfo = EncryptedDeviceInfo(productName = "App", manufacturer = "MA", softwareVersion = "1"),
                supportedRoles = listOf(VersionedRole.PLAYER_V1),
                playerV1Support = PlayerSupport(
                    supportedFormats = listOf(WireAudioFormat.from(AudioFormatSpec(AudioCodec.FLAC, 2, 48000, 16))),
                    bufferCapacity = 15_000_000,
                ),
                supportedPairMethods = pairMethods(WireVersion.SPEC_1_0, listOf("pairing_psk")),
                unpairedAccess = UnpairedAccess(false),
            ),
        )
        assertEquals(
            """{"type":"client/hello","payload":{"name":"Phone",""" +
                """"device_info":{"product_name":"App","manufacturer":"MA","software_version":"1"},""" +
                """"supported_roles":["player@v1"],""" +
                """"player@v1_support":{"supported_formats":[{"codec":"flac","channels":2,"sample_rate":48000,"bit_depth":16}],""" +
                """"buffer_capacity":15000000},""" +
                """"supported_pair_methods":{"pairing_psk":{}},"unpaired_access":{"enabled":false}}}""",
            WireCodec.encode(hello),
        )
    }

    @Test
    fun nullOptionalFieldsAreOmittedNotWrittenAsNull() {
        assertEquals(
            """{"product_name":"App"}""",
            SendspinJson.encodeToString(EncryptedDeviceInfo(productName = "App")),
        )
    }

    @Test
    fun truncateUtf8KeepsWholeCodePoints() {
        val emoji = "\uD83C\uDFB5" // 4 bytes, a surrogate pair
        assertEquals("a".repeat(60) + emoji, ("a".repeat(60) + emoji).truncateUtf8(64))
        assertEquals("a".repeat(62), ("a".repeat(62) + emoji).truncateUtf8(64))
        assertEquals("ї".repeat(32), "ї".repeat(40).truncateUtf8(64))
        assertEquals("€".repeat(21), "€".repeat(30).truncateUtf8(64))
        assertEquals("short", "short".truncateUtf8(64))
    }
}
