package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerQueueItemSerializationTest {
    @Test
    fun deserializesStreamDetailsWithoutDsp() {
        val json = """
            {
              "queue_item_id": "item-1",
              "streamdetails": {
                "provider": "library",
                "audio_format": {
                  "content_type": "audio/flac"
                }
              }
            }
        """.trimIndent()

        val item = myJson.decodeFromString<ServerQueueItem>(json)

        val streamDetails = assertNotNull(item.streamDetails)
        assertEquals(emptyMap(), streamDetails.dsp)
    }

    @Test
    fun deserializesLegacyDspMap() {
        val json = """
            {
              "queue_item_id": "item-1",
              "streamdetails": {
                "audio_format": {
                  "content_type": "audio/flac"
                },
                "dsp": {
                  "living-room": {
                    "output_format": {
                      "content_type": "audio/pcm",
                      "sample_rate": 96000,
                      "bit_depth": 24,
                      "channels": 2
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val item = myJson.decodeFromString<ServerQueueItem>(json)

        val dspSettings = assertNotNull(assertNotNull(item.streamDetails).dsp["living-room"])
        val outputFormat = assertNotNull(dspSettings.outputFormat)
        assertEquals("audio/pcm", outputFormat.contentType)
        assertEquals(96000, outputFormat.sampleRate)
        assertEquals(24, outputFormat.bitDepth)
        assertEquals(2, outputFormat.channels)
    }
}
