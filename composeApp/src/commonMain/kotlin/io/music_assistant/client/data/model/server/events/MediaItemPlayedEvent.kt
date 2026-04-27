package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItemPlayedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: MediaItemPlayedData,
) : Event<MediaItemPlayedData>

@Serializable
data class MediaItemPlayedData(
    @SerialName("uri") val uri: String,
    @SerialName("name") val name: String,
    @SerialName("duration") val duration: Double,
    @SerialName("seconds_played") val secondsPlayed: Double,
    @SerialName("fully_played") val fullyPlayed: Boolean,
    @SerialName("is_playing") val isPlaying: Boolean,
)
