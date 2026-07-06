package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [ServerMediaItem.position] must decode as Long across the full server range,
 * and only a plausible album ordinal may reach [Track.position] — an
 * out-of-Int-range value is dropped, never wrapped by `toInt()`.
 */
class ServerMediaItemSerializationTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private val outOfRangePosition = -1_727_938_860_000L
    private val validAlbumPosition = 3L

    private fun trackJson(position: Long) = """
        {"item_id":"t1","provider":"library","name":"Track",
         "media_type":"${MediaType.TRACK.serverValue}","position":$position}
    """.trimIndent()

    @Test
    fun decodesOutOfRangePositionWithoutOverflow() {
        val item = myJson.decodeFromString<ServerMediaItem>(trackJson(outOfRangePosition))

        assertEquals(outOfRangePosition, item.position)
    }

    @Test
    fun factoryDropsOutOfRangeTrackPosition() {
        val track = factory.create(
            myJson.decodeFromString<ServerMediaItem>(trackJson(outOfRangePosition)),
        ) as Track

        assertNull(track.position, "Implausible position must be dropped, not wrapped via toInt()")
    }

    @Test
    fun factoryKeepsValidTrackPosition() {
        val track = factory.create(
            myJson.decodeFromString<ServerMediaItem>(trackJson(validAlbumPosition)),
        ) as Track

        assertEquals(validAlbumPosition.toInt(), track.position)
    }
}
