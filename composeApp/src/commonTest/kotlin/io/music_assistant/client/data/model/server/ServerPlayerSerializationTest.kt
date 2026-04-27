package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.model.client.Player.Companion.toPlayer
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the tolerance contract of [ServerPlayer] deserialization: the
 * server evolves independently of the client, so any non-identity field
 * must be absent-tolerant and any enum field must survive an unknown
 * variant. Specifically:
 *
 *  - Only `player_id` is required; every other field has a default so a
 *    missing wire field does not throw `MissingFieldException`.
 *  - Unknown `PlayerType` / `PlayerState` variants coerce to `null` via
 *    `coerceInputValues = true` on [myJson], rather than throwing
 *    `SerializationException` out of `decodeTaggedEnum`.
 *  - [ServerPlayer.toPlayer] maps a null `type` back to [PlayerType.PLAYER]
 *    so a new server-side type still renders as something, instead of
 *    being silently misclassified as a group.
 */
class ServerPlayerSerializationTest {
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
    fun deserializesUnknownPlayerTypeAsNull() {
        val json = """{
            "player_id": "pl1",
            "type": "surround_group",
            "display_name": "Living Room"
        }"""

        val player = myJson.decodeFromString<ServerPlayer>(json)

        assertNull(player.type, "Unknown PlayerType must coerce to null, not throw")
        assertEquals("Living Room", player.displayName)
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
    fun deserializesKnownPlayerTypeNormally() {
        val json = """{"player_id": "pl1", "type": "group"}"""

        val player = myJson.decodeFromString<ServerPlayer>(json)

        assertEquals(PlayerType.GROUP, player.type)
    }

    @Test
    fun toPlayerMapsNullTypeToPlainPlayer() {
        val server = myJson.decodeFromString<ServerPlayer>("""{"player_id": "pl1"}""")

        val player = server.toPlayer()

        assertEquals(PlayerType.PLAYER, player.type)
        assertEquals(false, player.isGroup)
    }

    @Test
    fun toPlayerPreservesKnownGroupType() {
        val server = myJson.decodeFromString<ServerPlayer>(
            """{"player_id": "pl1", "type": "group"}""",
        )

        val player = server.toPlayer()

        assertEquals(PlayerType.GROUP, player.type)
        assertTrue(player.isGroup)
    }

    @Test
    fun deserializesInsideListEvenWhenOneElementHasUnknownEnum() {
        // RPCs returning List<ServerPlayer> must not lose the entire list when
        // one entry has a `type` the client doesn't know about — coercion is
        // applied per-element, not dropped at the collection boundary.
        val json = """[
            {"player_id": "good", "type": "player"},
            {"player_id": "weird", "type": "unknown_future_type"}
        ]"""

        val players = myJson.decodeFromString<List<ServerPlayer>>(json)

        assertEquals(2, players.size)
        assertEquals(PlayerType.PLAYER, players[0].type)
        assertNull(players[1].type)
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
