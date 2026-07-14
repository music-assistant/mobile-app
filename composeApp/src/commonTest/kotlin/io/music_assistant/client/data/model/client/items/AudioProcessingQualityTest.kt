package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.server.AudioFidelity
import io.music_assistant.client.data.model.server.AudioFidelitySummary
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.AudioInputDetails
import io.music_assistant.client.data.model.server.AudioOutputPath
import io.music_assistant.client.data.model.server.AudioProcessingChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioProcessingQualityTest {
    @Test
    fun usesMinimumOutputFidelityForBadge() {
        val chain = AudioProcessingChain(
            fidelity = AudioFidelitySummary(
                minOutputQuality = "standard",
                maxOutputQuality = "hi_res",
            ),
        )

        assertEquals(QualityTier.SQ, chain.qualityTier)
    }

    @Test
    fun unknownSummaryDoesNotFallBackToOutputFormat() {
        val chain = AudioProcessingChain(
            outputs = listOf(flacOutput()),
            fidelity = AudioFidelitySummary(
                minOutputQuality = "unknown",
                maxOutputQuality = "unknown",
            ),
        )

        assertNull(chain.qualityTier)
    }

    @Test
    fun unknownInputAndTranscodedFlacRemainUnknown() {
        val chain = AudioProcessingChain(
            input = AudioInputDetails(
                sourceFormat = AudioFormat(
                    contentType = "aac",
                    sampleRate = 44_100,
                    bitDepth = 16,
                ),
                fidelity = AudioFidelity(quality = "unknown"),
            ),
            outputs = listOf(
                flacOutput(),
            ),
        )

        assertNull(chain.qualityTier)
    }

    @Test
    fun unknownOutputFidelityDoesNotFallBackToOutputFormat() {
        val chain = AudioProcessingChain(
            outputs = listOf(
                flacOutput(fidelity = AudioFidelity(quality = "unknown")),
            ),
        )

        assertNull(chain.qualityTier)
    }

    @Test
    fun fallsBackToFinalOutputFormatWhenFidelityIsAbsent() {
        val chain = AudioProcessingChain(outputs = listOf(flacOutput()))

        assertEquals(QualityTier.HQ, chain.qualityTier)
    }

    private fun flacOutput(fidelity: AudioFidelity? = null) = AudioOutputPath(
        outputFormat = AudioFormat(
            contentType = "flac",
            sampleRate = 48_000,
            bitDepth = 24,
        ),
        fidelity = fidelity,
    )
}
