package io.music_assistant.client.ui.compose.common.providers

import kotlin.test.Test
import kotlin.test.assertEquals

class MdiIconTest {
    @Test
    fun normalizeStripsAllPrefixVariants() {
        listOf("mdi-speaker", "mdi:speaker", "mdi_speaker", "speaker", "  mdi-speaker  ")
            .forEach { assertEquals("speaker", normalizeMdiName(it)) }
    }

    @Test
    fun normalizeLeavesHyphensInsideName() {
        // Only the leading "mdi-" is a prefix; internal hyphens belong to the name.
        assertEquals("access-point", normalizeMdiName("mdi-access-point"))
    }

    @Test
    fun codePointInBasicPlaneIsSingleChar() {
        val s = codePointToString(0x0041) // 'A'
        assertEquals(1, s.length)
        assertEquals("A", s)
    }

    @Test
    fun supplementaryCodePointIsSurrogatePair() {
        // U+F04C3 ("speaker") lives above the BMP -> must encode as a 2-char surrogate pair.
        val s = codePointToString(0xF04C3)
        assertEquals(2, s.length)
        assertEquals(0xDB81.toChar(), s[0]) // high surrogate
        assertEquals(0xDCC3.toChar(), s[1]) // low surrogate
    }
}
