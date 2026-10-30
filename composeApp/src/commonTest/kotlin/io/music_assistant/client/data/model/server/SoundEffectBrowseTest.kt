package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.resolveLongClickActions
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SoundEffectBrowseTest {
    private val factory = MediaItemFactory(StubServiceClient())

    // The fields used by the app from the live music/browse responses for these providers.
    private fun browseItem(provider: String, id: String, name: String, duration: Int): SoundEffect {
        val payload = """[{"item_id":"$id","provider":"$provider","name":"$name",
            "media_type":"sound_effect","is_playable":true,
            "uri":"$provider://sound_effect/$id","duration":$duration}]"""
        val items = factory.createList(myJson.decodeFromString<List<ServerMediaItem>>(payload))
        assertEquals(1, items.size)
        return assertIs<SoundEffect>(items.single())
    }

    @Test
    fun `provider sounds survive browse mapping with their playable URI`() {
        listOf(
            browseItem("ambient_sounds", "white_noise", "White noise", 30),
            browseItem("ambient_sounds", "pink_noise", "Pink noise", 30),
            browseItem("ambient_sounds", "brown_noise", "Brown noise", 30),
            browseItem("ambient_sounds", "ocean_waves", "Ocean waves", 30),
            browseItem("rain_mood", "rain", "Rain", 0),
        ).forEach { item ->
            assertTrue(item.isPlayable)
            assertEquals("${item.provider}://sound_effect/${item.itemId}", item.mediaUri)
        }
    }

    @Test
    fun `browse sound menu offers queue actions without library or customization actions`() {
        val item = browseItem("rain_mood", "rain", "Rain", 0)
        val actions = resolveLongClickActions(
            item = item,
            clickContext = ClickContext.BROWSE,
            canEditLibrary = true,
            dislikeSupported = true,
            librarySupported = true,
            canAddToPlaylist = false,
            canRemoveFromPlaylist = false,
            progressSupported = false,
            playMediaSupport = PlayMediaSupport.ALL,
            defaultAction = ItemAction.Play(QueueOption.REPLACE),
            customizationAllowed = true,
        )
        assertEquals(
            listOf(
                ItemAction.Play(QueueOption.REPLACE),
                ItemAction.Play(QueueOption.PLAY),
                ItemAction.Play(QueueOption.NEXT),
                ItemAction.Play(QueueOption.ADD),
                ItemAction.Play(QueueOption.REPLACE_NEXT),
            ),
            actions,
        )
    }
}
