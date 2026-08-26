package io.music_assistant.client.sharedicons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import musicassistantclient.shared_icons.generated.resources.Res
import musicassistantclient.shared_icons.generated.resources.speaker
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedIconsTest {
    @Test
    fun `getResource returns speaker for unknown ID`() {
        val resource = SharedIcons.getResource("unknown")
        assertEquals(Res.drawable.speaker, resource)
    }

    @Test
    fun `getResource returns resource for every ID in manifest`() {
        val manifest = Json {}.decodeFromString<JsonObject>(MANIFEST_JSON)
        val iconIds = manifest["icons"]!!.jsonArray.map { it.jsonPrimitive.content }

        val uniqueResources = iconIds.map { SharedIcons.getResource(it) }.distinct()
        assertEquals(iconIds.size, uniqueResources.size)
    }
}

private const val MANIFEST_JSON = $$"""{
  "$schema": "./schema/manifest.schema.json",
  "version": "0.3.0",
  "fallback": "speaker",
  "icons": [
    "homepod-mini",
    "sonos",
    "mac",
    "apple-tv",
    "google-nest",
    "voice-pe",
    "wiim",
    "speaker",
    "speakers",
    "soundbar",
    "radio",
    "tv",
    "monitor",
    "laptop",
    "smartphone",
    "tablet",
    "headphones",
    "bluetooth",
    "airplay",
    "cast",
    "car",
    "music",
    "vinyl",
    "mic",
    "volume",
    "living-room",
    "bedroom",
    "bathroom",
    "toilet",
    "kitchen",
    "office",
    "hallway",
    "garden",
    "outdoor",
    "sun",
    "home",
    "building"
  ]
}
"""
