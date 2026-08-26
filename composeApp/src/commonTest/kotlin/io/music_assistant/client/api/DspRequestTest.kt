package io.music_assistant.client.api

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DspRequestTest {
    @Test
    fun applyPresetUsesDedicatedServerCommand() {
        val request = Request.Dsp.applyPreset("player-1", "night-mode")

        assertEquals(APICommands.CONFIG_PLAYERS_DSP_APPLY_PRESET, request.command)
        val args = assertNotNull(request.args)
        assertEquals("player-1", args.getValue("player_id").jsonPrimitive.content)
        assertEquals("night-mode", args.getValue("preset_id").jsonPrimitive.content)
    }
}
