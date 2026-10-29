package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A provider sends Markdown, HTML or both; the page shows plain text. */
class ItemDescriptionTest {
    @Test
    fun stripsTagsAndKeepsParagraphs() {
        val html = "<p>First <b>bold</b> line.</p><p>Second<br/>third</p>"

        assertEquals("First bold line.\nSecond\nthird", html.toPlainItemText())
    }

    @Test
    fun decodesEntitiesAfterTheTagsAreGone() {
        assertEquals(
            "Tom & Jerry <b>stay</b> 'quoted'",
            "Tom &amp; Jerry &lt;b&gt;stay&lt;/b&gt; &#39;quoted&#x27;".toPlainItemText(),
        )
        assertEquals("😀", "&#128512;".toPlainItemText())
        assertEquals("&#0;", "&#0;".toPlainItemText())
    }

    @Test
    fun literalLineBreaksBecomeLineBreaks() {
        assertEquals("one\ntwo", "one\\ntwo".toPlainItemText())
        assertEquals("one\n\ntwo", "one\r\n\r\n\r\n\r\ntwo  ".toPlainItemText())
    }

    @Test
    fun markdownLinksKeepTheirLabel() {
        assertEquals("See the site today", "See [the site](https://example.com) today".toPlainItemText())
    }

    @Test
    fun nothingReadableIsNull() {
        assertNull("<p></p>".toPlainItemText())
        assertNull("  \n ".toPlainItemText())
    }

    @Test
    fun albumReviewFallsBackToTheDescription() {
        val album = AppMediaItemFixtures.album()

        assertEquals(
            ItemDescription(ItemDescriptionKind.REVIEW, "A review"),
            album.copy(metadata = AppMediaItemFixtures.metadata(description = "Desc", review = "A review"))
                .itemDescription(),
        )
        assertEquals(
            ItemDescription(ItemDescriptionKind.REVIEW, "Desc"),
            album.copy(metadata = AppMediaItemFixtures.metadata(description = "Desc", review = "<p></p>"))
                .itemDescription(),
        )
        assertNull(album.itemDescription())
    }

    @Test
    fun eachTypeGetsItsKind() {
        val metadata = AppMediaItemFixtures.metadata(description = "Text")

        assertEquals(
            ItemDescriptionKind.BIOGRAPHY,
            AppMediaItemFixtures.artist().copy(metadata = metadata).itemDescription()?.kind,
        )
        assertEquals(
            ItemDescriptionKind.DESCRIPTION,
            AppMediaItemFixtures.playlist().copy(metadata = metadata).itemDescription()?.kind,
        )
        assertEquals(
            ItemDescriptionKind.DESCRIPTION,
            AppMediaItemFixtures.audiobook().copy(metadata = metadata).itemDescription()?.kind,
        )
        assertNull(AppMediaItemFixtures.track().copy(metadata = metadata).itemDescription())
    }
}
