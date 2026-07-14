package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioProcessingChainSerializationTest {
    @Test
    fun decodesSafeDefaults() {
        val chain = myJson.decodeFromString<AudioProcessingChain>("{}")

        assertEquals("", chain.queueId)
        assertNull(chain.queueItemId)
        assertEquals(0L, chain.revision)
        assertEquals("unknown", chain.state)
        assertNull(chain.input)
        assertNull(chain.queueProcessing)
        assertTrue(chain.outputs.isEmpty())
        assertNull(chain.fidelity)
        assertNull(myJson.decodeFromString<AudioInputDetails>("{}").fidelity)
        assertNull(myJson.decodeFromString<AudioOutputPath>("{}").fidelity)
    }

    @Test
    fun decodesFullSnapshotAndPreservesFutureValues() {
        val chain = myJson.decodeFromString<AudioProcessingChain>(FULL_SNAPSHOT)

        assertEquals("queue-1", chain.queueId)
        assertEquals("item-1", chain.queueItemId)
        assertEquals(3L, chain.revision)
        assertEquals("future_state", chain.state)

        val input = assertNotNull(chain.input)
        assertEquals("future_quality", input.fidelity?.quality)
        assertEquals(true, input.fidelity?.bitPerfect)
        assertEquals(96_000, input.sourceFormat?.sampleRate)

        val processing = assertNotNull(chain.queueProcessing)
        assertEquals("dynamic", processing.normalization?.mode)
        assertEquals("future_measurement", processing.normalization?.measurementSource)
        assertEquals(-1.2, processing.normalization?.appliedGainDb)
        assertEquals(1.25, processing.tempo?.playbackSpeed)
        assertEquals("applied", processing.crossfade?.state)
        assertEquals("Rain", processing.overlay?.source?.name)
        assertEquals(
            "future_image_shape",
            processing.overlay?.source?.image?.jsonPrimitive?.content,
        )

        val output = chain.outputs.single()
        assertEquals(listOf("player-1", "player-2"), output.playerIds)
        assertEquals("future_dsp_state", output.dsp.state)
        assertEquals(-1.0, output.dsp.inputGain)
        assertEquals(-0.5, output.dsp.outputGain)
        assertEquals("future_filter", output.dsp.filters.single().jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("left", output.channels?.mode)
        assertTrue(output.limiter.enabled)
        assertEquals("soxr", output.resampling?.method)
        assertEquals("triangular_hp", output.dithering?.method)
        assertEquals(48_000, output.outputFormat?.sampleRate)
        assertEquals(96_000, output.handoffFormat?.sampleRate)
        assertFalse(output.fidelity?.bitPerfect ?: true)
        assertEquals("future_summary", chain.fidelity?.maxOutputQuality)
    }

    companion object {
        private val FULL_SNAPSHOT = """
            {
              "queue_id": "queue-1",
              "queue_item_id": "item-1",
              "revision": 3,
              "state": "future_state",
              "future_top_level": true,
              "input": {
                "source_format": {
                  "content_type": "flac",
                  "sample_rate": 96000,
                  "bit_depth": 24,
                  "future_format_field": "ignored"
                },
                "server_input_format": {
                  "content_type": "pcm_s24le",
                  "sample_rate": 96000,
                  "bit_depth": 24
                },
                "fidelity": {
                  "quality": "future_quality",
                  "bit_perfect": true,
                  "future_fidelity_field": 1
                }
              },
              "queue_processing": {
                "input_format": {
                  "content_type": "pcm_s24le",
                  "sample_rate": 96000,
                  "bit_depth": 24
                },
                "output_format": {
                  "content_type": "pcm_f32le",
                  "sample_rate": 96000,
                  "bit_depth": 32
                },
                "normalization": {
                  "mode": "dynamic",
                  "measurement_source": "future_measurement",
                  "target_lufs": -14.0,
                  "measured_lufs": -17.2,
                  "applied_gain_db": -1.2,
                  "target_true_peak_dbtp": -2.0,
                  "target_loudness_range_lu": 10.0,
                  "reason_code": "future_reason"
                },
                "tempo": {"playback_speed": 1.25},
                "crossfade": {
                  "mode": "smart_crossfade",
                  "state": "applied",
                  "from_queue_item_id": "item-0",
                  "to_queue_item_id": "item-1",
                  "planned_duration": 8.0,
                  "actual_duration": 7.8,
                  "reason_code": null
                },
                "overlay": {
                  "source": {
                    "media_type": "sound_effect",
                    "item_id": "rain",
                    "provider": "builtin",
                    "name": "Rain",
                    "image": "future_image_shape",
                    "future_mapping_field": {}
                  },
                  "volume_percent": 40
                }
              },
              "outputs": [{
                "player_ids": ["player-1", "player-2"],
                "input_format": {
                  "content_type": "pcm_f32le",
                  "sample_rate": 96000,
                  "bit_depth": 32
                },
                "dsp": {
                  "state": "future_dsp_state",
                  "input_gain": -1.0,
                  "filters": [{
                    "type": "future_filter",
                    "enabled": true,
                    "future_filter_field": [1, 2]
                  }],
                  "output_gain": -0.5
                },
                "channels": {"mode": "left"},
                "limiter": {"enabled": true, "threshold_dbfs": -2.0},
                "resampling": {"method": "soxr"},
                "dithering": {"method": "triangular_hp"},
                "output_format": {
                  "content_type": "flac",
                  "sample_rate": 48000,
                  "bit_depth": 24
                },
                "handoff_format": {
                  "content_type": "pcm_f32le",
                  "sample_rate": 96000,
                  "bit_depth": 32
                },
                "fidelity": {"quality": "lossless", "bit_perfect": false}
              }],
              "fidelity": {
                "min_output_quality": "lossless",
                "max_output_quality": "future_summary"
              }
            }
        """.trimIndent()
    }
}
