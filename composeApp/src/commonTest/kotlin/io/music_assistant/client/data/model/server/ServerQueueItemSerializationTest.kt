package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerQueueItemSerializationTest {
    @Test
    fun deserializesStreamDetailsWithoutLegacyDsp() {
        val json = """
            {
              "queue_item_id": "queue-item-1",
              "streamdetails": {
                "provider": "library",
                "audio_format": {
                  "content_type": "audio/flac",
                  "sample_rate": 44100
                }
              }
            }
        """.trimIndent()

        val queueItem = myJson.decodeFromString<ServerQueueItem>(json)
        val streamDetails = assertNotNull(queueItem.streamDetails)

        assertEquals("library", streamDetails.provider)
        assertEquals("audio/flac", streamDetails.audioFormat.contentType)
        assertEquals(44_100, streamDetails.audioFormat.sampleRate)
        assertEquals(emptyMap(), streamDetails.dsp)
    }

    @Test
    fun deserializesStreamDetailsWithLegacyDsp() {
        val json = """
            {
              "queue_item_id": "queue-item-1",
              "streamdetails": {
                "audio_format": {
                  "content_type": "audio/flac"
                },
                "dsp": {
                  "player-1": {
                    "output_format": {
                      "content_type": "audio/pcm_s16le",
                      "sample_rate": 48000,
                      "bit_depth": 16,
                      "channels": 2
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val queueItem = myJson.decodeFromString<ServerQueueItem>(json)
        val streamDetails = assertNotNull(queueItem.streamDetails)
        val outputFormat = assertNotNull(streamDetails.dsp["player-1"]?.outputFormat)

        assertEquals("audio/pcm_s16le", outputFormat.contentType)
        assertEquals(48_000, outputFormat.sampleRate)
        assertEquals(16, outputFormat.bitDepth)
        assertEquals(2, outputFormat.channels)
    }
}
