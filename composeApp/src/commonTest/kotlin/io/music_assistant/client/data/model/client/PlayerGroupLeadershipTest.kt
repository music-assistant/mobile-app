package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [Player.canLeaveOwnGroup], which decides whether the Group Settings sheet draws
 * a leave button on the pivot player's own card.
 *
 * The predicate only gates the affordance — the server still classifies the player
 * itself when `players/cmd/ungroup` arrives — but it must not offer the gesture where
 * the server means something else by it, most importantly on a [PlayerType.GROUP]
 * player, where `ungroup` releases the whole session instead of transferring it.
 */
class PlayerGroupLeadershipTest {
    private val leader = PlayerDataFixtures.player(id = "leader").copy(
        groupMembers = setOf("member-a", "member-b"),
    )

    @Test
    fun adHocSyncLeaderCanLeave() {
        assertTrue(leader.canLeaveOwnGroup)
    }

    @Test
    fun soloPlayerCannotLeave() {
        assertFalse(PlayerDataFixtures.player().canLeaveOwnGroup)
        assertFalse(leader.copy(groupMembers = emptySet()).canLeaveOwnGroup)
    }

    @Test
    fun permanentGroupPlayerCannotLeave() {
        // `ungroup` on a PlayerType.GROUP powers it off / stops it — a different gesture.
        assertFalse(leader.copy(type = PlayerType.GROUP).canLeaveOwnGroup)
    }

    @Test
    fun followerCannotLeaveViaItsOwnCard() {
        // Members are removed from the leader's card, through `set_members`.
        assertFalse(leader.copy(syncedTo = "other-leader").canLeaveOwnGroup)
        assertFalse(leader.copy(activeGroup = "some-group").canLeaveOwnGroup)
    }

    @Test
    fun permanentMemberCannotLeave() {
        assertFalse(leader.copy(staticGroupMembers = setOf("leader")).canLeaveOwnGroup)
    }

    @Test
    fun staticMembershipOfOtherPlayersDoesNotBlockLeaving() {
        assertTrue(leader.copy(staticGroupMembers = setOf("member-a")).canLeaveOwnGroup)
    }
}
