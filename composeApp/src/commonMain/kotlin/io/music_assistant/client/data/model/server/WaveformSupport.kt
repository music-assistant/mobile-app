package io.music_assistant.client.data.model.server

/**
 * First server API schema that has `audio_analysis/wave_form`. The commit that added it left
 * `API_SCHEMA_VERSION` at 35, so 36 is the first version that guarantees it (every 2.10 server).
 */
const val WAVEFORM_MIN_SCHEMA = 36

/** Whether the server answers `audio_analysis/wave_form`. */
fun supportsWaveform(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= WAVEFORM_MIN_SCHEMA
