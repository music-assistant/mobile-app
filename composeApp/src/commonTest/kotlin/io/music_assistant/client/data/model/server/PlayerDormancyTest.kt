package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins down which server flags hide a player and which only put it to sleep.
 *
 * The server keeps a player it can no longer reach in the registry: it flips `available`
 * to false and sends a player update, then re-registers the player when the device comes
 * back. Deleting such a player makes a speaker vanish from the app while it is in stand-by
 * (issue #944), so only `enabled` / `hidden` / `hide_in_ui` may hide one. Everything else
 * folds into [io.music_assistant.client.data.model.client.Player.isPoweredOff], which
 * renders the player asleep and offers the power button.
 */
class PlayerDormancyTest {
    private val playerFactory = PlayerFactory(StubServiceClient())

    private fun player(json: String) = playerFactory.create(myJson.decodeFromString<ServerPlayer>(json))

    @Test
    fun `an unreachable player stays listed and reads as asleep`() {
        val player = player("""{"player_id": "ap1", "available": false}""")

        assertTrue(player.isListed)
        assertFalse(player.isAvailable)
        assertTrue(player.isPoweredOff)
        assertFalse(player.needsSetup)
    }

    @Test
    fun `a reachable powered-off player reads as asleep`() {
        val player = player(
            """{
                "player_id": "ap1",
                "available": true,
                "powered": false,
                "power_control": "fake",
                "supported_features": ["power"]
            }""",
        )

        assertTrue(player.isListed)
        assertTrue(player.isAvailable)
        assertTrue(player.isPoweredOff)
    }

    @Test
    fun `a reachable powered-on player is awake`() {
        val player = player(
            """{
                "player_id": "ap1",
                "available": true,
                "powered": true,
                "power_control": "fake",
                "supported_features": ["power"]
            }""",
        )

        assertFalse(player.isPoweredOff)
    }

    @Test
    fun `a player without power control is never asleep while reachable`() {
        val player = player("""{"player_id": "ap1", "available": true, "powered": false}""")

        assertFalse(player.canPower)
        assertFalse(player.isPoweredOff)
    }

    @Test
    fun `needs_setup is carried through so the label can say so`() {
        // The server serializes a player that waits for pairing as unavailable too, so
        // without this flag it would be mislabelled as merely asleep.
        val player = player("""{"player_id": "ap1", "available": false, "needs_setup": true}""")

        assertTrue(player.needsSetup)
        assertTrue(player.isListed)
        assertTrue(player.isPoweredOff)
    }

    @Test
    fun `disabled and hidden players stay hidden`() {
        assertFalse(player("""{"player_id": "p", "available": true, "enabled": false}""").isListed)
        assertFalse(player("""{"player_id": "p", "available": true, "hidden": true}""").isListed)
        assertFalse(player("""{"player_id": "p", "available": true, "hide_in_ui": true}""").isListed)
    }

    @Test
    fun `a payload without the availability field degrades to asleep rather than hidden`() {
        // `available` defaults to false. Before this change that deleted every player of a
        // server that stopped sending the field; now the worst case is a listed, sleeping one.
        val player = player("""{"player_id": "p"}""")

        assertTrue(player.isListed)
        assertTrue(player.isPoweredOff)
    }
}
