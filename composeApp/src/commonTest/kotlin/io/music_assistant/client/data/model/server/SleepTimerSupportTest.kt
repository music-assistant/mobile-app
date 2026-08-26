package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the sleep-timer schema gate. The server commit that added
 * the `players/sleep_timer` commands left `API_SCHEMA_VERSION` at 34, so 34 must stay
 * unsupported and 35 is the first version that guarantees the commands.
 */
class SleepTimerSupportTest {
    @Test
    fun unknownSchemaIsUnsupported() {
        assertFalse(supportsSleepTimer(null))
    }

    @Test
    fun schemaBelowThresholdIsUnsupported() {
        assertFalse(supportsSleepTimer(28))
        assertFalse(supportsSleepTimer(34))
    }

    @Test
    fun schemaAtOrAboveThresholdIsSupported() {
        assertTrue(supportsSleepTimer(35))
        assertTrue(supportsSleepTimer(LOCAL_SCHEMA_VERSION))
    }
}
