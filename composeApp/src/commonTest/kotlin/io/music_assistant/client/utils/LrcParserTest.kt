package io.music_assistant.client.utils

import io.music_assistant.client.data.model.client.LrcLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcParserTest {
    @Test
    fun `parses standard hundredths timestamps`() {
        val lines = LrcParser.parse(
            """
            [00:12.34]first
            [01:05.00]second
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LrcLine(12_340, "first"),
                LrcLine(65_000, "second"),
            ),
            lines,
        )
    }

    @Test
    fun `supports missing and three-digit fractions`() {
        val lines = LrcParser.parse(
            """
            [00:01]no frac
            [00:02.5]tenths
            [00:03.123]millis
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LrcLine(1_000, "no frac"),
                LrcLine(2_500, "tenths"),
                LrcLine(3_123, "millis"),
            ),
            lines,
        )
    }

    @Test
    fun `expands multiple timestamps on one line`() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]chorus")
        assertEquals(
            listOf(
                LrcLine(10_000, "chorus"),
                LrcLine(20_000, "chorus"),
            ),
            lines,
        )
    }

    @Test
    fun `sorts by time`() {
        val lines = LrcParser.parse(
            """
            [00:20.00]b
            [00:10.00]a
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(10_000, "a"), LrcLine(20_000, "b")), lines)
    }

    @Test
    fun `ignores metadata tags and blank lines`() {
        val lines = LrcParser.parse(
            """
            [ar:Artist]
            [ti:Title]

            [00:05.00]only synced line
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(5_000, "only synced line")), lines)
    }

    @Test
    fun `applies positive offset by shifting lines earlier`() {
        val lines = LrcParser.parse(
            """
            [offset:+250]
            [00:05.00]line
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(4_750, "line")), lines)
    }

    @Test
    fun `applies negative offset by shifting lines later`() {
        val lines = LrcParser.parse(
            """
            [offset:-1000]
            [00:05.00]line
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(6_000, "line")), lines)
    }

    @Test
    fun `floors offset-shifted time at zero`() {
        val lines = LrcParser.parse(
            """
            [offset:+10000]
            [00:05.00]line
            """.trimIndent(),
        )
        assertEquals(listOf(LrcLine(0, "line")), lines)
    }

    @Test
    fun `returns empty for plain unsynced text`() {
        assertTrue(LrcParser.parse("just some lyrics\nwith no timestamps").isEmpty())
    }

    @Test
    fun `keeps empty text lines for timing`() {
        val lines = LrcParser.parse("[00:30.00]")
        assertEquals(listOf(LrcLine(30_000, "")), lines)
    }

    @Test
    fun `parses timestamps of 100 minutes and more`() {
        val lines = LrcParser.parse(
            """
            [98:10.00]line A
            [102:30.00]line B
            [107:27.00]line C
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                LrcLine(5_890_000, "line A"),
                LrcLine(6_150_000, "line B"),
                LrcLine(6_447_000, "line C"),
            ),
            lines,
        )
    }
}
