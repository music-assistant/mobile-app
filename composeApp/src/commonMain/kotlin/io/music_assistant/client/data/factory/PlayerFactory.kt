package io.music_assistant.client.data.factory

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerMedia
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.server.PlayerFeature
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.data.model.server.ServerPlayerMedia

/**
 * Maps server-side [ServerPlayer] DTOs (and their nested [ServerPlayerMedia])
 * to client-side [Player] / [PlayerMedia] models.
 */
class PlayerFactory(
    private val apiClient: ServiceClient,
) {
    fun create(server: ServerPlayer): Player = with(server) {
        Player(
            id = playerId,
            name = displayName,
            provider = provider,
            icon = icon,
            // Unknown/new server-side player types (coerced to null by myJson) are
            // treated as regular players so they still show up and aren't mistaken
            // for a group. If the server adds a genuinely new group-like type we
            // can surface it explicitly once the mobile app learns about it.
            type = PlayerType.fromServer(type) ?: PlayerType.PLAYER,
            shouldBeShown = available && enabled && (hidden != true) && (hideInUi != true),
            canSetVolume = supportedFeatures.contains(PlayerFeature.VOLUME_SET),
            canPower = supportedFeatures.contains(PlayerFeature.POWER) && powerControl != null && powerControl != PLAYER_CONTROL_NONE,
            isPowered = powered == true,
            volumeLevel = volumeLevel,
            volumeControl = volumeControl,
            volumeMuted = volumeMuted == true,
            canMute = supportedFeatures.contains(PlayerFeature.VOLUME_MUTE) && muteControl != null && muteControl != PLAYER_CONTROL_NONE,
            queueId = activeSource ?: currentMedia?.queueId,
            isPlaying = state == PlayerState.PLAYING,
            isAnnouncing = announcementInProgress == true,
            canGroupWith = canGroupWith,
            groupMembers = groupMembers,
            staticGroupMembers = staticGroupMembers,
            activeGroup = activeGroup,
            syncedTo = syncedTo,
            groupVolume = groupVolume,
            groupVolumeMuted = groupVolumeMuted == true,
            currentMedia = currentMedia?.let(::createPlayerMedia),
        )
    }

    fun createList(servers: List<ServerPlayer>): List<Player> =
        servers.map { create(it) }

    private fun createPlayerMedia(server: ServerPlayerMedia): PlayerMedia = with(server) {
        val clientMediaType = MediaType.fromServer(mediaType)
        PlayerMedia(
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl?.let(apiClient::rebaseServerImageUrl),
            duration = duration.takeIf { clientMediaType != MediaType.RADIO },
            queueId = queueId,
            queueItemId = queueItemId,
            mediaType = clientMediaType,
            uri = uri,
        )
    }

    private companion object {
        const val PLAYER_CONTROL_NONE = "none"
    }
}
