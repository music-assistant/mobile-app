package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.PlayerData.ChildBind
import io.music_assistant.client.data.model.server.PlayerType
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.UniqueIdGenerator

object PlayerDataFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun playerData(
        queueId: String = "queue${uniqueIdGenerator.nextInt()}",
        name: String = "Player ${uniqueIdGenerator.nextInt()}",
        groupChildren: List<ChildBind> = emptyList(),
        playerType: PlayerType = PlayerType.PLAYER,
    ): PlayerData {
        return PlayerData(
            player = Player(
                id = "player${uniqueIdGenerator.nextInt()}",
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
            ),
            queue = DataState.Data(
                Queue(
                    info = QueueInfo(
                        id = queueId,
                        available = true,
                        shuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        elapsedTime = 100.0,
                        elapsedTimeLastUpdated = null,
                        currentItem = null,
                    ),
                    items = DataState.NoData(),
                ),
            ),
            parentBind = null,
            childrenBinds = groupChildren,
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
}
