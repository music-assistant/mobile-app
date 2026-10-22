package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.server.AudioFidelity.Companion.QUALITY_HI_RES
import io.music_assistant.client.data.model.server.AudioFidelity.Companion.QUALITY_LOSSLESS
import io.music_assistant.client.data.model.server.AudioFidelity.Companion.QUALITY_LOW
import io.music_assistant.client.data.model.server.AudioFidelity.Companion.QUALITY_STANDARD
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.AudioProcessingChain

data class QueueTrack(
    val id: String,
    val track: PlayableItem,
    val isPlayable: Boolean,
    val format: AudioFormat?,
    val provider: String?,
    val audioProcessingChain: AudioProcessingChain? = null,
    /** The container the item was played from (album, playlist, folder...). */
    val origin: AppMediaItem? = null,
)

enum class QualityTier {
    LQ, SQ, HQ, HR
}

val QueueTrack.qualityTier: QualityTier?
    get() {
        val inputQuality = mapQualityTier(audioProcessingChain?.inputFidelity?.quality)
        val outputQualities =
            audioProcessingChain?.outputs.orEmpty().mapNotNull { mapQualityTier(it.fidelity?.quality) }
        val outputQuality = outputQualities.minOrNull()

        return if (inputQuality != null && outputQuality != null) {
            listOf(inputQuality, outputQuality).min()
        } else {
            inputQuality
        }
    }

private fun mapQualityTier(inputQuality: String?): QualityTier? {
    return when (inputQuality) {
        QUALITY_LOW -> QualityTier.LQ
        QUALITY_STANDARD -> QualityTier.SQ
        QUALITY_LOSSLESS -> QualityTier.HQ
        QUALITY_HI_RES -> QualityTier.HR
        else -> null
    }
}
