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

    // Audiobook authors/narrators arrive as plain strings (legacy) or as
    // Artist/ItemMapping objects (current server dev); both must decode.
    private val audiobookJson = """
        {"item_id":"1560","provider":"library","name":"10 Blind Dates",
         "media_type":"audiobook","is_playable":true,
         "authors":[
           {"item_id":"a1","provider":"library","name":"Ashley Elston",
            "media_type":"artist","available":true,"is_playable":true,
            "image":null,"year":null},
           "Plain String Author"
         ],
         "narrators":[
           {"item_id":"n1","provider":"library","name":"Nora Narrator",
            "media_type":"artist","available":true}
         ]}
    """.trimIndent()

    @Test
    fun decodesAuthorsAndNarratorsFromObjectsOrStrings() {
        val item = myJson.decodeFromString<ServerMediaItem>(audiobookJson)

        assertEquals(listOf("Ashley Elston", "Plain String Author"), item.authors)
        assertEquals(listOf("Nora Narrator"), item.narrators)
    }
}
