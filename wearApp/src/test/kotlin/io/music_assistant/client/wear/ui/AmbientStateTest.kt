package io.music_assistant.client.wear.ui

import androidx.compose.ui.unit.DpOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmbientStateTest {

    private fun ambient(burnIn: Boolean = true, update: Int = 0) =
        AmbientState.Ambient(
            burnInProtectionRequired = burnIn,
            lowBitAmbient = false,
            updateCount = update,
        )

    @Test
    fun `an awake screen is not ambient and never shifts`() {
        assertFalse(AmbientState.Interactive.isAmbient)
        assertEquals(DpOffset.Zero, AmbientState.Interactive.burnInOffset)
    }

    @Test
    fun `a panel that does not ask for burn-in protection does not pay for it`() {
        assertTrue(ambient(burnIn = false).isAmbient)
        (0..8).forEach { update ->
            assertEquals(DpOffset.Zero, ambient(burnIn = false, update = update).burnInOffset)
        }
    }

    @Test
    fun `the shift walks a square and comes back round`() {
        val walk = (0..3).map { ambient(update = it).burnInOffset }
        assertEquals(4, walk.distinct().size, "each update moves the content somewhere new")
        assertEquals(walk, (4..7).map { ambient(update = it).burnInOffset })
    }

    @Test
    fun `the shift stays inside the margin the labels keep from the glass`() {
        (0..7).forEach { update ->
            val offset = ambient(update = update).burnInOffset
            assertTrue(
                maxOf(kotlin.math.abs(offset.x.value), kotlin.math.abs(offset.y.value)) <= 8f,
                "a shift of $offset would push the title into the bezel",
            )
        }
    }

    @Test
    fun `the walk averages out, so no direction is favoured`() {
        val round = (0..3).map { ambient(update = it).burnInOffset }
        assertEquals(0f, round.sumOf { it.x.value.toDouble() }.toFloat())
        assertEquals(0f, round.sumOf { it.y.value.toDouble() }.toFloat())
    }
}
