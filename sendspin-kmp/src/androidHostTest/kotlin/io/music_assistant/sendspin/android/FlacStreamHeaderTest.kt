package io.music_assistant.sendspin.android

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class FlacStreamHeaderTest {
    private val marker = byteArrayOf(0x66, 0x4C, 0x61, 0x43)
    private val streamInfo = ByteArray(34) { (it + 1).toByte() }
    private val expected = marker + byteArrayOf(0x80.toByte(), 0, 0, 34) + streamInfo

    @Test
    fun cutsTheBlocksAfterStreamInfoAndMarksItLast() {
        val vendorComment = byteArrayOf(0x84.toByte(), 0, 0, 4, 9, 9, 9, 9)
        val config = marker + byteArrayOf(0, 0, 0, 34) + streamInfo + vendorComment
        assertContentEquals(expected, flacStreamHeader(config))
    }

    @Test
    fun wrapsABareStreamInfoBlock() {
        assertContentEquals(expected, flacStreamHeader(streamInfo))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(flacStreamHeader(ByteArray(10)))
        assertNull(flacStreamHeader(ByteArray(50)))
    }
}
