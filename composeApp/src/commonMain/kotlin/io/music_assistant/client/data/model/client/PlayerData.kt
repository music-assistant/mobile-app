package io.music_assistant.client.data.model.client

import io.music_assistant.client.ui.compose.common.DataState

data class PlayerData(
    val player: Player,
    val queue: DataState<Queue>,
    val parentBind: ParentBind?,
    val childrenBinds: List<ChildBind>,
    val isLocal: Boolean = false,
    val pendingPlay: Boolean = false,
) {
    val playerId = player.id
    val queueInfo = (queue as? DataState.Data)?.data?.info
    val queueItems = ((queue as? DataState.Data)?.data?.items as? DataState.Data)?.data
    val queueId = queueInfo?.id
    val queueOrPlayerId = queueId ?: player.id

    fun updateFrom(other: PlayerData): PlayerData {
        return PlayerData(
            player = other.player,
            queue = when (queue) {
                is DataState.Data -> {
                    when (other.queue) {
                        is DataState.Loading -> queue
                        is DataState.Error -> queue
                        is DataState.NoData -> queue
                        is DataState.Stale -> queue  // Preserve data during stale state
                        is DataState.Data -> {
                            // Preserve queue items if new data doesn't have them loaded
                            val oldQueueData = queue.data
                            val newQueueData = other.queue.data
                            when (newQueueData.items) {
                                is DataState.NoData -> {
                                    // Keep old items, but update queue info
                                    DataState.Data(
                                        newQueueData.copy(items = oldQueueData.items),
                                    )
                                }

                                else -> other.queue
                            }
                        }
                    }
                }

                else -> other.queue
            },
            parentBind = other.parentBind,
            childrenBinds = other.childrenBinds,
            isLocal = other.isLocal,
            pendingPlay = other.pendingPlay,
        )
    }

    data class ChildBind(
        val id: String,
        val parentId: String,
        val volume: Float?,
        val volumeSliderAccessible: Boolean,
        val isMuted: Boolean?,
        val name: String,
        val isBound: Boolean,
        val isManageable: Boolean,
    )

    data class ParentBind(
        val id: String,
        val name: String,
        val isPlaying: Boolean,
        val isGroup: Boolean,
    )
}
