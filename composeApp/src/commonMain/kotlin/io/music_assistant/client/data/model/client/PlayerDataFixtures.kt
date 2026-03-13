package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.PlayerData.Bind
import io.music_assistant.client.data.model.server.PlayerType
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.UniqueIdGenerator

object PlayerDataFixtures {

    private val uniqueIdGenerator = UniqueIdGenerator()

    fun playerData(
        queueId: String = "queue${uniqueIdGenerator.nextInt()}",
        name: String = "Player ${uniqueIdGenerator.nextInt()}",
        groupChildren: List<Bind> = emptyList()
    ): PlayerData {
        return PlayerData(
            player = Player(
                id = "player${uniqueIdGenerator.nextInt()}",
                name = name,
                provider = "provider",
                type = PlayerType.PLAYER,
                shouldBeShown = true,
                canSetVolume = true,
                volumeLevel = 50f,
                volumeMuted = false,
                canMute = true,
                queueId = queueId,
                isPlaying = true,
                isAnnouncing = false,
                canGroupWith = emptyList(),
                groupVolume = null,
                groupMembers = null,
                staticGroupMembers = null
            ),
            queue = DataState.Data(
                Queue(
                    info = QueueInfo(
                        id = queueId,
                        available = true,
                        shuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        elapsedTime = 100.0,
                        currentItem = null,
                    ),
                    items = DataState.NoData()
                )
            ),
            groupChildren = groupChildren,
        )
    }

    fun bind(): Bind {
        return Bind(
            id = "bind${uniqueIdGenerator.nextInt()}",
            parentId = "bind${uniqueIdGenerator.nextInt()}",
            volume = null,
            isMuted = null,
            name = "Player ${uniqueIdGenerator.nextInt()}",
            isBound = false,
            isManageable = true
        )
    }
}