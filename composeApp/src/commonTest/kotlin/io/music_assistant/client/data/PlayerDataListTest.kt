package io.music_assistant.client.data

import io.music_assistant.client.data.MainDataSource.Companion.mergePlayerDataList
import io.music_assistant.client.data.model.client.PlayerDataFixtures.player
import io.music_assistant.client.data.model.client.PlayerDataFixtures.playerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [MainDataSource.mergePlayerDataList]: the local player comes from the adapter whether or not
 * the server lists it and carries its sync parent. It takes part in grouping, as a child
 * candidate and as a leader, only while the server lists it, like in the web frontend.
 */
class PlayerDataListTest {
    private val leader = player(id = "leader", name = "Kitchen").copy(
        canGroupWith = listOf("provider"),
        groupMembers = setOf("leader", LOCAL_ID),
    )
    private val localMember = playerData(queueId = "leader").let { data ->
        data.copy(player = data.player.copy(id = LOCAL_ID, name = "Phone", syncedTo = "leader"), isLocal = true)
    }

    @Test
    fun `a hidden local member is pinned first with its leader as parent`() {
        val list = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader),
            sortedIds = null,
            queues = emptyList(),
            localData = localMember,
            oldPlayers = emptyList(),
        )

        assertEquals(listOf(LOCAL_ID, "leader"), list.map { it.playerId })
        assertEquals("leader", list.first().parentBind?.id)
        assertTrue(list.first().isLocal)
    }

    @Test
    fun `the leader offers the listed local player as a bound child and not a hidden one`() {
        val hidden = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader),
            sortedIds = null,
            queues = emptyList(),
            localData = localMember,
            oldPlayers = emptyList(),
        )
        val listed = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader, localMember.player),
            sortedIds = null,
            queues = emptyList(),
            localData = localMember,
            oldPlayers = emptyList(),
        )

        assertTrue(hidden.single { it.playerId == "leader" }.childrenBinds.isEmpty())
        val child = listed.single { it.playerId == "leader" }.childrenBinds.single()
        assertEquals(LOCAL_ID, child.id)
        assertTrue(child.isBound)
    }

    @Test
    fun `a listed local player without a parent leads and a hidden one does not`() {
        val alone = localMember.copy(
            player = localMember.player.copy(syncedTo = null, canGroupWith = listOf("provider")),
        )
        val other = player(id = "office", name = "Office")
        val hidden = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(other),
            sortedIds = null,
            queues = emptyList(),
            localData = alone,
            oldPlayers = emptyList(),
        )
        val listed = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(other, alone.player),
            sortedIds = null,
            queues = emptyList(),
            localData = alone,
            oldPlayers = emptyList(),
        )

        assertTrue(hidden.first().childrenBinds.isEmpty())
        assertEquals(listOf("office"), listed.first().childrenBinds.map { it.id })
        assertTrue(listed.single { it.playerId == LOCAL_ID }.isLocal)
    }

    @Test
    fun `a listed local player is not duplicated and keeps the adapter data`() {
        val serverCopy = localMember.player.copy(name = "Phone (server)")
        val list = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(serverCopy, leader),
            sortedIds = listOf("leader", LOCAL_ID),
            queues = emptyList(),
            localData = localMember,
            oldPlayers = emptyList(),
        )

        assertEquals(listOf(LOCAL_ID, "leader"), list.map { it.playerId })
        assertEquals("Phone", list.first().player.name)
    }

    @Test
    fun `a local player without a group has no parent and the others have no local child`() {
        val alone = localMember.copy(player = localMember.player.copy(syncedTo = null))
        val other = player(id = "office", name = "Office")
        val list = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(other),
            sortedIds = null,
            queues = emptyList(),
            localData = alone,
            oldPlayers = emptyList(),
        )

        assertNull(list.first().parentBind)
        assertTrue(list.single { it.playerId == "office" }.childrenBinds.isEmpty())
    }

    @Test
    fun `the local queue re-anchors on the tracked elapsed time only when the server has one`() {
        val tracked = requireNotNull(localMember.queueInfo).copy(elapsedTime = 42.0)
        val withTracked = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader),
            sortedIds = null,
            queues = listOf(tracked),
            localData = localMember,
            oldPlayers = emptyList(),
        )
        val withoutTracked = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader),
            sortedIds = null,
            queues = emptyList(),
            localData = localMember,
            oldPlayers = emptyList(),
        )

        assertEquals(42.0, withTracked.first().queueInfo?.elapsedTime)
        assertEquals(100.0, withoutTracked.first().queueInfo?.elapsedTime)
    }

    @Test
    fun `without adapter data the list has no local entry`() {
        val list = mergePlayerDataList(
            localPlayerId = LOCAL_ID,
            serverPlayers = listOf(leader),
            sortedIds = null,
            queues = emptyList(),
            localData = null,
            oldPlayers = emptyList(),
        )

        assertEquals(listOf("leader"), list.map { it.playerId })
    }

    private companion object {
        const val LOCAL_ID = "phone"
    }
}
