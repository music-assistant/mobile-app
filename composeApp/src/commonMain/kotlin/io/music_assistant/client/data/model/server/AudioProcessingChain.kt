package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Full runtime processing snapshot for a queue.
 *
 * Evolution-prone server enums intentionally stay as raw strings. This keeps a
 * future value local to the field instead of rejecting the complete snapshot.
 */
@Serializable
data class AudioProcessingChain(
    @SerialName("queue_id") val queueId: String = "",
    @SerialName("queue_item_id") val queueItemId: String? = null,
    @SerialName("revision") val revision: Long = 0,
    @SerialName("state") val state: String = "unknown",
    @SerialName("input") val input: AudioInputDetails? = null,
    @SerialName("queue_processing") val queueProcessing: AudioQueueProcessing? = null,
    @SerialName("outputs") val outputs: List<AudioOutputPath> = emptyList(),
    @SerialName("fidelity") val fidelity: AudioFidelitySummary? = null,
)

@Serializable
data class AudioFidelity(
    @SerialName("quality") val quality: String = "unknown",
    @SerialName("bit_perfect") val bitPerfect: Boolean? = null,
)

@Serializable
data class AudioFidelitySummary(
    @SerialName("min_output_quality") val minOutputQuality: String = "unknown",
    @SerialName("max_output_quality") val maxOutputQuality: String = "unknown",
)

@Serializable
data class AudioInputDetails(
    @SerialName("source_format") val sourceFormat: AudioFormat? = null,
    @SerialName("server_input_format") val serverInputFormat: AudioFormat? = null,
    @SerialName("fidelity") val fidelity: AudioFidelity? = null,
)

@Serializable
data class AudioNormalizationDetails(
    @SerialName("mode") val mode: String = "unknown",
    @SerialName("measurement_source") val measurementSource: String = "unknown",
    @SerialName("target_lufs") val targetLufs: Double? = null,
    @SerialName("measured_lufs") val measuredLufs: Double? = null,
    @SerialName("applied_gain_db") val appliedGainDb: Double? = null,
    @SerialName("target_true_peak_dbtp") val targetTruePeakDbtp: Double? = null,
    @SerialName("target_loudness_range_lu") val targetLoudnessRangeLu: Double? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
)

@Serializable
data class AudioTempoDetails(
    @SerialName("playback_speed") val playbackSpeed: Double = 1.0,
)

@Serializable
data class AudioCrossfadeDetails(
    @SerialName("mode") val mode: String = "unknown",
    @SerialName("state") val state: String = "unknown",
    @SerialName("from_queue_item_id") val fromQueueItemId: String? = null,
    @SerialName("to_queue_item_id") val toQueueItemId: String? = null,
    @SerialName("planned_duration") val plannedDuration: Double? = null,
    @SerialName("actual_duration") val actualDuration: Double? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
)

@Serializable
data class AudioItemMapping(
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("sort_name") val sortName: String? = null,
    @SerialName("uri") val uri: String? = null,
    @SerialName("available") val available: Boolean? = null,
    @SerialName("image") val image: JsonElement? = null,
    @SerialName("year") val year: Int? = null,
)

@Serializable
data class AudioOverlayDetails(
    @SerialName("source") val source: AudioItemMapping? = null,
    @SerialName("volume_percent") val volumePercent: Int = 100,
)

@Serializable
data class AudioQueueProcessing(
    @SerialName("input_format") val inputFormat: AudioFormat? = null,
    @SerialName("output_format") val outputFormat: AudioFormat? = null,
    @SerialName("normalization") val normalization: AudioNormalizationDetails? = null,
    @SerialName("tempo") val tempo: AudioTempoDetails? = null,
    @SerialName("crossfade") val crossfade: AudioCrossfadeDetails? = null,
    @SerialName("overlay") val overlay: AudioOverlayDetails? = null,
)

@Serializable
data class AudioDspDetails(
    @SerialName("state") val state: String = "unknown",
    @SerialName("input_gain") val inputGain: Double = 0.0,
    @SerialName("filters") val filters: List<JsonElement> = emptyList(),
    @SerialName("output_gain") val outputGain: Double = 0.0,
)

@Serializable
data class AudioChannelDetails(
    @SerialName("mode") val mode: String = "unknown",
)

@Serializable
data class AudioLimiterDetails(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("threshold_dbfs") val thresholdDbfs: Double? = null,
)

@Serializable
data class AudioResamplingDetails(
    @SerialName("method") val method: String = "unknown",
)

@Serializable
data class AudioDitheringDetails(
    @SerialName("method") val method: String = "unknown",
)

@Serializable
data class AudioOutputPath(
    @SerialName("player_ids") val playerIds: List<String> = emptyList(),
    @SerialName("input_format") val inputFormat: AudioFormat? = null,
    @SerialName("dsp") val dsp: AudioDspDetails = AudioDspDetails(),
    @SerialName("channels") val channels: AudioChannelDetails? = null,
    @SerialName("limiter") val limiter: AudioLimiterDetails = AudioLimiterDetails(),
    @SerialName("resampling") val resampling: AudioResamplingDetails? = null,
    @SerialName("dithering") val dithering: AudioDitheringDetails? = null,
    @SerialName("output_format") val outputFormat: AudioFormat? = null,
    @SerialName("handoff_format") val handoffFormat: AudioFormat? = null,
    @SerialName("fidelity") val fidelity: AudioFidelity? = null,
)
