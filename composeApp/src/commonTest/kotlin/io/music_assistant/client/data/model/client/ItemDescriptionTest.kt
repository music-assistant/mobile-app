package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A provider sends Markdown, HTML or both; the page shows plain text with its links. */
class ItemDescriptionTest {
    @Test
    fun stripsTagsAndKeepsParagraphs() {
        val html = "<p>First <b>bold</b> line.</p><p>Second<br/>third</p>"

        assertEquals("First bold line.\nSecond\nthird", html.toItemText()?.text)
    }

    @Test
    fun decodesEntitiesAfterTheTagsAreGone() {
        assertEquals(
            "Tom & Jerry <b>stay</b> 'quoted'",
            "Tom &amp; Jerry &lt;b&gt;stay&lt;/b&gt; &#39;quoted&#x27;".toItemText()?.text,
        )
        assertEquals("😀", "&#128512;".toItemText()?.text)
        assertEquals("&#0;", "&#0;".toItemText()?.text)
    }

    @Test
    fun literalLineBreaksBecomeLineBreaks() {
        assertEquals("one\ntwo", "one\\ntwo".toItemText()?.text)
        assertEquals("one\n\ntwo", "one\r\n\r\n\r\n\r\ntwo  ".toItemText()?.text)
    }

    @Test
    fun markdownLinkLinksItsLabel() {
        assertEquals(
            ItemText("See the site today", listOf(ItemLink(4, 12, "https://example.com"))),
            "See [the site](https://example.com \"Title\") today".toItemText(),
        )
    }

    @Test
    fun htmlAnchorLinksItsLabelAndDecodesTheUrl() {
        assertEquals(
            ItemText("Go here & there", listOf(ItemLink(3, 15, "https://e.com/?a=1&b=2"))),
            """Go <a class="x" href="https://e.com/?a=1&amp;b=2"><b>here &amp; there</b></a>""".toItemText(),
        )
        assertEquals(
            ItemText("one two", listOf(ItemLink(0, 3, "http://a.b"), ItemLink(4, 7, "mailto:x@y.z"))),
            "<A HREF='http://a.b'>one</A> <a href=mailto:x@y.z>two</a>".toItemText(),
        )
    }

    @Test
    fun unsafeOrEmptyLinksStayText() {
        assertEquals(ItemText("click"), """<a href="javascript:alert(1)">click</a>""".toItemText())
        assertEquals(ItemText("rel"), "[rel](/relative/path)".toItemText())
        assertEquals(ItemText("Logo:"), """Logo:<a href="https://e.com"><img src="x.png"></a>""".toItemText())
    }

    @Test
    fun bareUrlsAreLinkedWithoutSentencePunctuation() {
        assertEquals(
            ItemText(
                "Visit https://e.com/a. Or (https://en.wikipedia.org/wiki/X_(band)).",
                listOf(ItemLink(6, 21, "https://e.com/a"), ItemLink(27, 65, "https://en.wikipedia.org/wiki/X_(band)")),
            ),
            "Visit https://e.com/a. Or (https://en.wikipedia.org/wiki/X_(band)).".toItemText(),
        )
        assertEquals(
            ItemText("see https://e.com", listOf(ItemLink(4, 17, "https://e.com"))),
            "see <https://e.com>".toItemText(),
        )
    }

    @Test
    fun aUrlLabelIsOneLink() {
        assertEquals(
            ItemText("https://e.com", listOf(ItemLink(0, 13, "https://e.com"))),
            """<a href="https://e.com">https://e.com</a>""".toItemText(),
        )
    }

    @Test
    fun aLinkInsideALinkLabelKeepsTheOuterLink() {
        assertEquals(
            ItemText("a b", listOf(ItemLink(0, 3, "https://outer.com"))),
            """<a href="https://outer.com">a [b](https://inner.com)</a>""".toItemText(),
        )
    }

    @Test
    fun markerCharactersInTheSourceAreDropped() {
        assertEquals(ItemText("ab"), "a\u0001\u0002\u0003b".toItemText())
    }

    @Test
    fun nothingReadableIsNull() {
        assertNull("<p></p>".toItemText()?.text)
        assertNull("  \n ".toItemText()?.text)
    }

    @Test
    fun albumReviewFallsBackToTheDescription() {
        val album = AppMediaItemFixtures.album()

        assertEquals(
            ItemDescription(ItemDescriptionKind.REVIEW, ItemText("A review")),
            album.copy(metadata = AppMediaItemFixtures.metadata(description = "Desc", review = "A review"))
                .itemDescription(),
        )
        assertEquals(
            ItemDescription(ItemDescriptionKind.REVIEW, ItemText("Desc")),
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
