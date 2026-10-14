package io.music_assistant.client.ui.compose.home.players

import kotlin.test.Test
import kotlin.test.assertEquals

/** The dialog offers 5..200 % in 5 % steps; the server accepts 0..200, so values outside clamp. */
class AmbientSoundVolumeTest {
    @Test
    fun `snaps to the 5 percent grid`() {
        assertEquals(80, snapOverlayVolume(81.4f))
        assertEquals(85, snapOverlayVolume(83f))
    }

    @Test
    fun `clamps to the offered range`() {
        assertEquals(5, snapOverlayVolume(0f))
        assertEquals(200, snapOverlayVolume(250f))
    }
}
