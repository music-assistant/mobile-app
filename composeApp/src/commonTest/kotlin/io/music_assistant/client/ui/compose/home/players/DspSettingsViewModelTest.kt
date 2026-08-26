package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.server.DspConfig
import io.music_assistant.client.data.model.server.DspConfigPreset
import io.music_assistant.client.data.model.server.supportsDspApplyPreset
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DspSettingsViewModelTest {
    @Test
    fun applyPresetSupportStartsAtSchema38() {
        assertFalse(supportsDspApplyPreset(null))
        assertFalse(supportsDspApplyPreset(37))
        assertTrue(supportsDspApplyPreset(38))
        assertTrue(supportsDspApplyPreset(53))
    }

    @Test
    fun dspConfigDecodingSupportsAbsentNullAndPresentPresetId() {
        val base = buildJsonObject {
            put("enabled", true)
            put("input_gain", 0.0)
            put("output_gain", 0.0)
            put("filters", JsonArray(emptyList()))
        }

        assertNull(myJson.decodeFromJsonElement<DspConfig>(base).presetId)
        assertNull(
            myJson.decodeFromJsonElement<DspConfig>(
                JsonObject(base + ("preset_id" to JsonNull)),
            ).presetId,
        )
        assertEquals(
            "modern",
            myJson.decodeFromJsonElement<DspConfig>(
                JsonObject(base + ("preset_id" to JsonPrimitive("modern"))),
            ).presetId,
        )
    }

    @Test
    fun serverPresetIdResolvesToDisplayedPresetKey() {
        val matchingPreset = preset("room", "Room")
        val otherPreset = preset("studio", "Studio")

        assertEquals(
            "room" to "Room",
            appliedDspPresetKey(
                DspConfig(presetId = "room"),
                listOf(matchingPreset, otherPreset),
            ),
        )
        assertNull(appliedDspPresetKey(DspConfig(presetId = "missing"), listOf(matchingPreset)))
        assertNull(appliedDspPresetKey(DspConfig(), listOf(matchingPreset)))
    }

    private fun preset(id: String?, name: String) = DspConfigPreset(
        name = name,
        config = DspConfig(filters = JsonArray(emptyList())),
        presetId = id,
    )
}
