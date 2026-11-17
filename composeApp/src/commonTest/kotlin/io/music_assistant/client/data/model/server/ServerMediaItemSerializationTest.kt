package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.utils.mediaItemEchoJson
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun factoryPreservesDynamicRadioFlag() {
        val item = myJson.decodeFromString<ServerMediaItem>(
            """
                {"item_id":"radio-1","provider":"provider","name":"Dynamic Radio",
                 "media_type":"radio","is_dynamic":true,"is_playable":true}
            """.trimIndent(),
        )

        assertEquals(true, item.isDynamic)
        assertEquals(true, (factory.create(item) as RadioStation).isDynamic)
    }

    // A feed-parsed podcast carries the feed's website link in `uri`; the MA uri is rebuilt.
    @Test
    fun factoryBuildsPodcastUriFromIdentity() {
        val item = myJson.decodeFromString<ServerMediaItem>(
            """
                {"item_id":"https://feed.example/rss","provider":"itunes_podcasts--x",
                 "name":"Podcast","media_type":"podcast","uri":"https://www.example.com/show"}
            """.trimIndent(),
        )

        assertEquals(
            "itunes_podcasts--x://podcast/https://feed.example/rss",
            (factory.create(item) as Podcast).uri,
        )
    }

    private fun episodeJson(numbers: String) = """
        {"item_id":"e1","provider":"podcastfeed--x","name":"Episode",
         "media_type":"${MediaType.PODCAST_EPISODE.serverValue}","position":1$numbers}
    """.trimIndent()

    @Test
    fun factoryCarriesEpisodeAndSeasonNumbers() {
        val episode = factory.create(
            myJson.decodeFromString<ServerMediaItem>(episodeJson(""","episode_number":5,"season":2""")),
        ) as PodcastEpisode

        assertEquals(5, episode.episodeNumber)
        assertEquals(2, episode.season)
    }

    // Server 2.11 sends null for an episode the publisher does not number.
    @Test
    fun decodesNullEpisodeNumbers() {
        val item = myJson.decodeFromString<ServerMediaItem>(episodeJson(""","episode_number":null,"season":null"""))

        assertNull(item.episodeNumber)
        assertNull(item.season)
    }

    // Server 2.10 sends neither key.
    @Test
    fun decodesEpisodeWithoutNumberKeys() {
        val episode = factory.create(myJson.decodeFromString<ServerMediaItem>(episodeJson(""))) as PodcastEpisode

        assertNull(episode.episodeNumber)
        assertNull(episode.season)
    }

    // `music/mark_played` echoes the DTO back; a 2.10 server must get no new keys.
    @Test
    fun echoOmitsAbsentEpisodeNumbers() {
        val echoed = mediaItemEchoJson.encodeToString(
            ServerMediaItem.serializer(),
            myJson.decodeFromString<ServerMediaItem>(episodeJson("")),
        )

        assertFalse("episode_number" in echoed)
        assertFalse("season" in echoed)
    }

    // The refresh entry reads the availability of each mapping. The server always sends the key.
    @Test
    fun decodesMappingAvailability() {
        val item = myJson.decodeFromString<ServerMediaItem>(
            """
            {"item_id":"t1","provider":"library","name":"Track","media_type":"${MediaType.TRACK.serverValue}",
             "provider_mappings":[
               {"item_id":"1","provider_domain":"spotify","provider_instance":"spotify--1","available":false},
               {"item_id":"2","provider_domain":"tidal","provider_instance":"tidal--1"}]}
            """.trimIndent(),
        )

        assertEquals(listOf(false, true), item.providerMappings?.map { it.available })
    }
}
