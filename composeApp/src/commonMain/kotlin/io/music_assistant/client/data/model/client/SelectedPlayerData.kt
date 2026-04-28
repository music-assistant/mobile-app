package io.music_assistant.client.data.model.client

data class SelectedPlayerData(
    val playerId: String,
    val queueItems: List<QueueTrack>? = null,
    val chosenItemsIds: Set<String> = emptySet(),
)
