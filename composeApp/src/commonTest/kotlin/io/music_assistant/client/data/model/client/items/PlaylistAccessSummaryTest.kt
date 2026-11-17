package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The owner line of a Music Assistant playlist, with the words of the web frontend. */
class PlaylistAccessSummaryTest {
    private val me = "user-me"
    private val other = "user-other"

    private fun access(
        owner: String? = other,
        sharing: PlaylistSharing? = PlaylistSharing.SELECTED,
        sharedUsers: List<String> = listOf(me, "user-3"),
        collaborative: Boolean = false,
    ) = PlaylistAccess(owner, sharing, sharedUsers, collaborative)

    @Test
    fun noRecordServesEveryone() {
        assertEquals(
            listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.Everyone),
            null.summaryParts(viewerId = me, ownerName = null),
        )
    }

    @Test
    fun theViewerOwnsIt() {
        assertEquals(
            listOf(PlaylistAccessPart.Personal, PlaylistAccessPart.OnlyMe),
            access(owner = me, sharing = PlaylistSharing.PRIVATE).summaryParts(me, ownerName = "Ignored"),
        )
        assertEquals(
            listOf(PlaylistAccessPart.Personal, PlaylistAccessPart.SharedWith(2), PlaylistAccessPart.Collaborative),
            access(owner = me, collaborative = true).summaryParts(me, ownerName = null),
        )
    }

    @Test
    fun anotherOwnerIsNamedOnlyWhenKnown() {
        assertEquals(
            listOf(PlaylistAccessPart.Owner("Alex"), PlaylistAccessPart.SharedWith(2)),
            access().summaryParts(me, ownerName = "Alex"),
        )
        assertEquals(
            listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.SharedWith(2)),
            access().summaryParts(me, ownerName = null),
        )
        assertEquals(
            listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.NotShared),
            access(sharing = PlaylistSharing.PRIVATE).summaryParts(me, ownerName = null),
        )
    }

    @Test
    fun noOwnerIsSharedEvenWhenANameIsGiven() {
        assertEquals(
            listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.Members),
            access(owner = null, sharing = PlaylistSharing.MEMBERS).summaryParts(me, ownerName = "Alex"),
        )
        assertEquals(
            listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.Everyone),
            access(owner = null, sharing = PlaylistSharing.EVERYONE).summaryParts(null, ownerName = null),
        )
    }

    @Test
    fun anUnknownSharingValueSkipsTheSharingPart() {
        assertEquals(
            listOf(PlaylistAccessPart.Personal, PlaylistAccessPart.Collaborative),
            access(owner = me, sharing = null, collaborative = true).summaryParts(me, ownerName = null),
        )
    }

    private val builtin = AppMediaItemFixtures.playlist().copy(
        providerMappings = listOf(ProviderMapping("1", "builtin", "builtin--1")),
        owner = "Alex",
    )

    @Test
    fun ownerLineUsesTheRecordOnlyForAMusicAssistantPlaylistOnAServerThatSendsIt() {
        assertEquals(
            PlaylistOwnerLine.Access(listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.Everyone)),
            builtin.ownerLine(accessSupported = true, viewerId = me, ownerName = null),
        )
        assertEquals(
            PlaylistOwnerLine.Access(listOf(PlaylistAccessPart.Owner("Alex"), PlaylistAccessPart.SharedWith(2))),
            builtin.copy(access = access()).ownerLine(accessSupported = true, viewerId = me, ownerName = "Alex"),
        )
        // 2.10 sends no record, so a null record says nothing: the owner name is all there is.
        assertEquals(
            PlaylistOwnerLine.Owner("Alex"),
            builtin.ownerLine(accessSupported = false, viewerId = me, ownerName = null),
        )
    }

    @Test
    fun aServicePlaylistShowsItsOwnerName() {
        val service = AppMediaItemFixtures.playlist().copy(owner = "Spotify")

        assertEquals(PlaylistOwnerLine.Owner("Spotify"), service.ownerLine(accessSupported = true, me, null))
        assertNull(service.copy(owner = " ").ownerLine(accessSupported = true, me, null))
        assertNull(AppMediaItemFixtures.playlist().ownerLine(accessSupported = true, me, null))
    }
}
