package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the tolerance contract of [ServerPlayer] deserialization and the
 * client-side enum mapping done by [PlayerFactory]. The server evolves
 * independently of the client, so:
 *
 *  - Only `player_id` is required; every other field has a default so a
 *    missing wire field does not throw `MissingFieldException`.
 *  - `type` is decoded as a raw string (no `@Serializable` enum on the wire),
 *    so an unknown server-side value can't break decoding.
 *  - [PlayerFactory.create] maps `type` through [PlayerType.fromServer],
 *    falling back to [PlayerType.PLAYER] for unknown/missing values so a new
 *    server type still renders as something instead of being misclassified
 *    as a group.
 *  - `state` is still a `@Serializable` enum and relies on
 *    `coerceInputValues = true` in [myJson] for unknown variants.
 */
class ServerPlayerSerializationTest {
    private val playerFactory = PlayerFactory(FakeClient())

    @Test
    fun deserializesWithOnlyPlayerIdPresent() {
        val json = """{"player_id": "pl1"}"""

        val player = myJson.decodeFromString<ServerPlayer>(json)

        assertEquals("pl1", player.playerId)
        assertEquals("", player.provider)
        assertNull(player.type)
        assertEquals(false, player.available)
        assertTrue(player.supportedFeatures.isEmpty())
        assertEquals(true, player.enabled)
        assertEquals("", player.displayName)
        assertEquals("", player.volumeControl)
    }

    @Test
    fun deserializesUnknownPlayerTypeAsRawString() {
        val json = """{
            "player_id": "pl1",
            "type": "surround_group",
            "display_name": "Living Room"
        }"""

        val server = myJson.decodeFromString<ServerPlayer>(json)

        assertEquals("surround_group", server.type)
        assertEquals("Living Room", server.displayName)
        // Factory must reject the unknown variant and fall back to PLAYER.
        assertEquals(PlayerType.PLAYER, playerFactory.create(server).type)
    }

    @Test
    fun deserializesUnknownPlayerStateAsNull() {
        val json = """{
            "player_id": "pl1",
            "state": "buffering"
        }"""

        val player = myJson.decodeFromString<ServerPlayer>(json)

        assertNull(player.state, "Unknown PlayerState must coerce to null, not throw")
    }

    @Test
    fun toPlayerMapsKnownGroupType() {
        val server = myJson.decodeFromString<ServerPlayer>(
            """{"player_id": "pl1", "type": "group"}""",
        )

        val player = playerFactory.create(server)

        assertEquals(PlayerType.GROUP, player.type)
        assertTrue(player.isGroup)
    }

    @Test
    fun toPlayerMapsMissingTypeToPlainPlayer() {
        val server = myJson.decodeFromString<ServerPlayer>("""{"player_id": "pl1"}""")

        val player = playerFactory.create(server)

        assertEquals(PlayerType.PLAYER, player.type)
        assertEquals(false, player.isGroup)
    }

    @Test
    fun deserializesInsideListEvenWhenOneElementHasUnknownEnum() {
        // RPCs returning List<ServerPlayer> must not lose the entire list when
        // one entry has a `type` the client doesn't know about — the raw
        // string carries through and the factory handles the fallback per item.
        val json = """[
            {"player_id": "good", "type": "player"},
            {"player_id": "weird", "type": "unknown_future_type"}
        ]"""

        val servers = myJson.decodeFromString<List<ServerPlayer>>(json)

        assertEquals(2, servers.size)
        assertEquals(PlayerType.PLAYER, playerFactory.create(servers[0]).type)
        assertEquals(PlayerType.PLAYER, playerFactory.create(servers[1]).type)
    }

    @Test
    fun toleratesUnknownTopLevelFields() {
        val json = """{
            "player_id": "pl1",
            "brand_new_field_server_added_last_week": { "nested": true }
        }"""

        val player = myJson.decodeFromString<ServerPlayer>(json)

        assertEquals("pl1", player.playerId)
    }
}
