package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the `play_media` flag gates. The server commits that added `start_from_beginning` and
 * `shuffle` left `API_SCHEMA_VERSION` at 39 and 51, so 40 and 52 are the first versions that
 * guarantee each flag.
 */
class PlayMediaSupportTest {
    @Test
    fun unknownSchemaSupportsNoFlag() {
        assertEquals(PlayMediaSupport.NONE, playMediaSupport(null))
    }

    @Test
    fun startFromBeginningNeedsSchema40() {
        assertEquals(false, playMediaSupport(39).startFromBeginning)
        assertEquals(true, playMediaSupport(40).startFromBeginning)
    }

    @Test
    fun shuffleNeedsSchema52() {
        assertEquals(false, playMediaSupport(51).shuffle)
        assertEquals(true, playMediaSupport(52).shuffle)
    }

    @Test
    fun theLocalSchemaSupportsBothFlags() {
        assertEquals(
            PlayMediaSupport(shuffle = true, startFromBeginning = true),
            playMediaSupport(LOCAL_SCHEMA_VERSION),
        )
    }
}
