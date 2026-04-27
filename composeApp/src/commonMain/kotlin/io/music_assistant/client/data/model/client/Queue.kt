package io.music_assistant.client.data.model.client

import io.music_assistant.client.ui.compose.common.DataState

data class Queue(
    val info: QueueInfo,
    val items: DataState<List<QueueTrack>>,
)
