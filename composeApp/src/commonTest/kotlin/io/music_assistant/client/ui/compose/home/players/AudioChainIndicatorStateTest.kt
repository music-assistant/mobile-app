package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.items.QualityTier
import io.music_assistant.client.data.model.server.AudioFidelitySummary
import io.music_assistant.client.data.model.server.AudioProcessingChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioChainIndicatorStateTest {
    @Test
    fun authoritativeUnknownChainKeepsDetailsAvailable() {
        val state = audioChainIndicatorState(
            processingChain = AudioProcessingChain(
                fidelity = AudioFidelitySummary(
                    minOutputQuality = "unknown",
                    maxOutputQuality = "unknown",
                ),
            ),
            legacyTier = QualityTier.HQ,
        )

        assertTrue(state.hasDetails)
        assertNull(state.tier)
    }

    @Test
    fun noChainOrLegacyTierHasNoDetailsTrigger() {
        val state = audioChainIndicatorState(
            processingChain = null,
            legacyTier = null,
        )

        assertFalse(state.hasDetails)
        assertNull(state.tier)
    }

    @Test
    fun absentServerFidelityRetainsLegacyTier() {
        val state = audioChainIndicatorState(
            processingChain = AudioProcessingChain(),
            legacyTier = QualityTier.SQ,
        )

        assertTrue(state.hasDetails)
        assertEquals(QualityTier.SQ, state.tier)
    }
}
