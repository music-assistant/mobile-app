package io.music_assistant.client.data.model.server

/**
 * First server API schema with `players/sleep_timer/set` and the
 * `sleep_timer_expires_at` player field. The commit that added the sleep timer
 * left `API_SCHEMA_VERSION` at 34, so 35 is the first version that guarantees it.
 */
const val SLEEP_TIMER_MIN_SCHEMA = 35

fun supportsSleepTimer(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SLEEP_TIMER_MIN_SCHEMA
