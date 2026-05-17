package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.DSPSettings

data class QueueTrack(
    val id: String,
    val track: PlayableItem,
    val isPlayable: Boolean,
    val format: AudioFormat?,
    val dsp: Map<String, DSPSettings>?,
) {
    fun audioFormat(playerId: String) = dsp?.get(playerId)?.outputFormat ?: format
}
