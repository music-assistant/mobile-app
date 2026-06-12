package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.AppMediaItemFixtures.track
import io.music_assistant.client.data.model.client.PlayerData.ChildBind
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.UniqueIdGenerator

object PlayerDataFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun playerData(
        queueId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Player ${uniqueIdGenerator.nextInt()}",
        groupChildren: List<ChildBind> = emptyList(),
        playerType: PlayerType = PlayerType.PLAYER,
    ): PlayerData {
        return PlayerData(
            player = player(name = name, playerType = playerType, queueId = queueId),
            queue = DataState.Data(
                Queue(
                    info = QueueInfo(
                        id = queueId,
                        available = true,
                        currentIndex = null,
                        shuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        dontStopTheMusicEnabled = false,
                        elapsedTime = 100.0,
                        elapsedTimeLastUpdated = null,
                        currentItem = null,
                        radioSource = emptyList(),
                    ),
                    items = DataState.NoData(),
                ),
            ),
            parentBind = null,
            childrenBinds = groupChildren,
        )
    }

    fun playerData(queue: Queue): PlayerData {
        val playerData = playerData()
        return playerData.copy(
            player = playerData.player.copy(
                queueId = queue.info.id,
                currentMedia = (queue.items as DataState.Data<List<QueueTrack>>).data.first()
                    .toPlayerMedia(),
            ),
            queue = DataState.Data(queue),
        )
    }

    fun player(
        id: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Player ${uniqueIdGenerator.nextInt()}",
        playerType: PlayerType = PlayerType.PLAYER,
        queueId: String = uniqueIdGenerator.nextInt().toString(),
        currentMedia: PlayerMedia? = null,
    ): Player {
        return Player(
            id = id,
            icon = "mdi-speaker",
            name = name,
            provider = "provider",
            type = playerType,
            shouldBeShown = true,
            canSetVolume = true,
            volumeLevel = 50f,
            volumeMuted = false,
            volumeControl = "native",
            canMute = true,
            queueId = queueId,
            isPlaying = true,
            isAnnouncing = false,
            canGroupWith = emptyList(),
            groupVolume = null,
            groupVolumeMuted = false,
            groupMembers = null,
            staticGroupMembers = null,
            activeGroup = null,
            syncedTo = null,
            currentMedia = currentMedia,
        )
    }

    fun bind(): ChildBind {
        return ChildBind(
            id = "bind${uniqueIdGenerator.nextInt()}",
            parentId = "bind${uniqueIdGenerator.nextInt()}",
            volume = null,
            volumeSliderAccessible = false,
            isMuted = null,
            name = "Player ${uniqueIdGenerator.nextInt()}",
            isBound = false,
            isManageable = true,
        )
    }

    fun List<QueueTrack>.toQueue(hasRadio: Boolean = false): Queue {
        val queueId = uniqueIdGenerator.nextInt().toString()
        val queueInfo = QueueInfo(
            id = queueId,
            available = true,
            currentIndex = null,
            shuffleEnabled = false,
            repeatMode = RepeatMode.OFF,
            dontStopTheMusicEnabled = false,
            elapsedTime = 100.0,
            elapsedTimeLastUpdated = null,
            currentItem = first(),
            radioSource = if (hasRadio) {
                listOf(track())
            } else {
                emptyList()
            },
        )

        return Queue(info = queueInfo, items = DataState.Data(this))
    }

    fun PlayableItem.toPlayerMedia(
        queueId: String = uniqueIdGenerator.nextInt().toString(),
        queueItemId: String = uniqueIdGenerator.nextInt().toString(),
    ): PlayerMedia {
        return PlayerMedia(
            title = displayName,
            artist = subtitle,
            album = (this as? Track)?.album?.displayName,
            imageUrl = null,
            duration = duration,
            queueId = queueId,
            queueItemId = queueItemId,
            mediaType = (this as? AppMediaItem)?.mediaType,
            uri = null,
            palette = null,
        )
    }

    fun QueueTrack.toPlayerMedia(
        queueId: String = uniqueIdGenerator.nextInt().toString(),
    ): PlayerMedia {
        return track.toPlayerMedia(
            queueId = queueId,
            queueItemId = id,
        )
    }

    fun PlayableItem.toQueueTrack(
        id: String = uniqueIdGenerator.nextInt().toString(),
    ): QueueTrack {
        return QueueTrack(
            id = id,
            track = this,
            isPlayable = true,
            format = null,
            dsp = null,
            provider = null,
        )
    }
}
