package io.music_assistant.sendspin.wire

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BinaryFramesTest {
    private fun body(timestamp: Long, payload: ByteArray): ByteArray {
        val out = ByteArray(BinaryFrames.TIMESTAMP_BYTES + payload.size)
        for (i in 0 until 8) out[i] = (timestamp shr (8 * (7 - i))).toByte()
        payload.copyInto(out, BinaryFrames.TIMESTAMP_BYTES)
        return out
    }

    @Test
    fun audioChunkIsAViewIntoTheBody() {
        val payload = byteArrayOf(9, 8, 7)
        val bytes = body(0x0102030405060708L, payload)
        val parsed = BinaryFrames.parse(4, bytes)
        assertIs<BinaryFrame.Audio>(parsed)
        val chunk = parsed.chunk
        assertEquals(0x0102030405060708L, chunk.timestampMicros)
        assertSame(bytes, chunk.body)
        assertEquals(BinaryFrames.TIMESTAMP_BYTES, chunk.offset)
        assertEquals(3, chunk.length)
        assertContentEquals(payload, chunk.body.copyOfRange(chunk.offset, chunk.offset + chunk.length))
    }

    @Test
    fun emptyPayloadIsValid() {
        val parsed = BinaryFrames.parse(4, body(5, ByteArray(0)))
        assertIs<BinaryFrame.Audio>(parsed)
        assertEquals(0, parsed.chunk.length)
    }

    @Test
    fun nonAudioTypesAreReportedNotParsed() {
        assertEquals(BinaryFrame.Other(5), BinaryFrames.parse(5, body(1, byteArrayOf(1))))
        assertEquals(BinaryFrame.Other(7), BinaryFrames.parse(7, byteArrayOf()))
    }

    @Test
    fun shortOrNegativeTimestampBodiesAreMalformed() {
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(4, ByteArray(7)))
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(4, body(-1L, byteArrayOf(1))))
    }
}
