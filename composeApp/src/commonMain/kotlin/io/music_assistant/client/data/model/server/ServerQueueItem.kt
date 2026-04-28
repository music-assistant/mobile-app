package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerQueueItem(
    @SerialName("queue_item_id") val queueItemId: String,
    @SerialName("media_item") val mediaItem: ServerMediaItem? = null,
    // @SerialName("queue_id") val queueId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("duration") val duration: Double? = null,
    // @SerialName("sort_index") val sortIndex: Int,
    @SerialName("streamdetails") val streamDetails: StreamDetails? = null,
    @SerialName("image") val image: MediaItemImage? = null,
    // @SerialName("index") val index: Int
)

@Serializable
data class StreamDetails(
//    @SerialName("provider") val provider: String,
//    @SerialName("item_id") val itemId: String,
    @SerialName("audio_format") val audioFormat: AudioFormat,
//    @SerialName("media_type") val mediaType: String,
//    @SerialName("stream_type") val streamType: String,
//    @SerialName("duration") val duration: Double? = null,
//    @SerialName("size") val size: Int? = null,
//    @SerialName("stream_title") val streamTitle: String? = null,
//    @SerialName("loudness") val loudness: Double? = null,
//    @SerialName("loudness_album") val loudnessAlbum: Double? = null,
//    @SerialName("prefer_album_loudness") val preferAlbumLoudness: Boolean,
//    @SerialName("volume_normalization_mode") val volumeNormalizationMode: String,
//    @SerialName("volume_normalization_gain_correct") val volumeNormalizationGainCorrect: Double? = null,
//    @SerialName("target_loudness") val targetLoudness: Double,
//    @SerialName("strip_silence_begin") val stripSilenceBegin: Boolean,
//    @SerialName("strip_silence_end") val stripSilenceEnd: Boolean,
    @SerialName("dsp") val dsp: Map<String, DSPSettings>,
)

@Serializable
data class DSPSettings(
//    @SerialName("state") val state: String,
//    @SerialName("input_gain") val inputGain: Double,
//    @SerialName("filters") val filters: List<String>,
//    @SerialName("output_gain") val outputGain: Double,
//    @SerialName("output_limiter") val outputLimiter: Boolean,
    @SerialName("output_format") val outputFormat: AudioFormat? = null,
)
