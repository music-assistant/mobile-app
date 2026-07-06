package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Browse folders (`media_type: folder`) carry a dedicated `path` (distinct from `uri`) and often
 * arrive with a blank `name`; the parent entry is named "..". This guards the parse → factory →
 * model chain that drives folder navigation and titles.
 */
class BrowseFolderMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private fun folderJson(name: String, path: String?) = buildString {
        append("""{"item_id":"f1","provider":"tidal","media_type":"${MediaType.FOLDER.serverValue}"""")
        append(""","name":"$name"""")
        path?.let { append(""","path":"$it"""") }
        append("}")
    }

    private fun folder(name: String, path: String?): RecommendationFolder =
        factory.create(myJson.decodeFromString<ServerMediaItem>(folderJson(name, path)))
            as RecommendationFolder

    @Test
    fun `path is parsed and carried onto the folder`() {
        val server = myJson.decodeFromString<ServerMediaItem>(
            folderJson(name = "Tidal", path = "tidal--6rdvd8iR://"),
        )

        assertEquals("tidal--6rdvd8iR://", server.path)
        assertEquals("tidal--6rdvd8iR://", folder("Tidal", "tidal--6rdvd8iR://").path)
    }

    @Test
    fun `a real name is used verbatim as the display name`() {
        assertEquals("Tidal", folder(name = "Tidal", path = "tidal--6rdvd8iR://").displayName)
    }

    @Test
    fun `a blank name falls back to the capitalized last path segment`() {
        assertEquals("Artists", folder(name = "", path = "tidal--6rdvd8iR://artists").displayName)
    }

    @Test
    fun `a trailing slash is trimmed before taking the last segment`() {
        assertEquals("Albums", folder(name = "", path = "tidal--6rdvd8iR://albums/").displayName)
    }

    @Test
    fun `a blank name with no path yields an empty display name`() {
        assertEquals("", folder(name = "", path = null).displayName)
    }

    @Test
    fun `the parent entry is flagged but real folders are not`() {
        assertTrue(folder(name = "..", path = "root").isParentLink)
        assertFalse(folder(name = "", path = "tidal--6rdvd8iR://tracks").isParentLink)
        assertFalse(folder(name = "Tidal", path = "tidal--6rdvd8iR://").isParentLink)
    }
}
