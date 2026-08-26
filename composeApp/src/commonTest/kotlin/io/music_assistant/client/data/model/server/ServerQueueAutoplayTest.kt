package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins autoplay decoding across the MA 2.10 rename.
 *
 * The radio indicator broke because the client read a legacy key (`radio_source`) that
 * 2.10 still emits but hard-codes to empty. Autoplay went through the same rename —
 * `dont_stop_the_music_enabled` -> `autoplay_enabled` — but its legacy key IS still
 * mirrored honestly. These pin both spellings so a future server drop of the mirror
 * cannot silently switch the badge off.
 */
class ServerQueueAutoplayTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun decode(json: String) = myJson.decodeFromString<ServerQueue>(json)

    /** The client-facing flag the badge and the overflow menu both read. */
    private fun autoPlayEnabled(json: String) = queueFactory.create(decode(json)).autoPlayEnabled

    @Test
    fun modernKeyIsRead() {
        val queue = decode("""{"queue_id": "q1", "autoplay_enabled": true}""")

        assertEquals(true, queue.autoplayEnabled)
        assertNull(queue.dontStopTheMusicEnabled)
    }

    @Test
    fun legacyKeyStillWorksForOlderServers() {
        val queue = decode("""{"queue_id": "q1", "dont_stop_the_music_enabled": true}""")

        assertNull(queue.autoplayEnabled)
        assertEquals(true, queue.dontStopTheMusicEnabled)
    }

    @Test
    fun bothPresentKeepsBothSoModernCanWin() {
        // 2.10 sends both; the factory prefers `autoplayEnabled`.
        val queue = decode(
            """{"queue_id": "q1", "autoplay_enabled": false, "dont_stop_the_music_enabled": false}""",
        )

        assertEquals(false, queue.autoplayEnabled)
        assertEquals(false, queue.dontStopTheMusicEnabled)
    }

    @Test
    fun neitherPresentMeansNoAutoplaySupport() {
        val queue = decode("""{"queue_id": "q1"}""")

        assertNull(queue.autoplayEnabled)
        assertNull(queue.dontStopTheMusicEnabled)
    }

    @Test
    fun factoryPrefersTheModernKeyOverTheDeprecatedOne() {
        // A server that has switched off autoplay but still mirrors a stale legacy value
        // must read as off, not on.
        assertEquals(
            false,
            autoPlayEnabled(
                """{"queue_id": "q1", "autoplay_enabled": false, "dont_stop_the_music_enabled": true}""",
            ),
        )
    }

    @Test
    fun factoryFallsBackToTheLegacyKey() {
        assertEquals(
            true,
            autoPlayEnabled("""{"queue_id": "q1", "dont_stop_the_music_enabled": true}"""),
        )
    }

    @Test
    fun factoryReportsNullWhenTheServerSendsNeither() {
        // Null is the feature gate: no autoplay support, so no badge and no menu item.
        assertNull(autoPlayEnabled("""{"queue_id": "q1"}"""))
    }
}
