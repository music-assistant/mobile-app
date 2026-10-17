package io.music_assistant.sendspin.wire

import io.music_assistant.sendspin.api.WireVersion
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BinaryFramesTest {
    private fun body(timestamp: Long, payload: ByteArray, sendAhead: Long? = null): ByteArray {
        val header = BinaryFrames.TIMESTAMP_BYTES + (sendAhead?.let { BinaryFrames.SEND_AHEAD_BYTES } ?: 0)
        val out = ByteArray(header + payload.size)
        for (i in 0 until 8) out[i] = (timestamp shr (8 * (7 - i))).toByte()
        sendAhead?.let { for (i in 0 until 4) out[8 + i] = (it shr (8 * (3 - i))).toByte() }
        payload.copyInto(out, header)
        return out
    }

    @Test
    fun audioChunkIsAViewIntoTheBody() {
        val payload = byteArrayOf(9, 8, 7)
        val bytes = body(0x0102030405060708L, payload)
        val parsed = BinaryFrames.parse(WireVersion.LEGACY_9, 4, bytes)
        assertIs<BinaryFrame.Audio>(parsed)
        val chunk = parsed.chunk
        assertEquals(0x0102030405060708L, chunk.timestampMicros)
        assertSame(bytes, chunk.body)
        assertEquals(BinaryFrames.TIMESTAMP_BYTES, chunk.offset)
        assertEquals(3, chunk.length)
        assertContentEquals(payload, chunk.body.copyOfRange(chunk.offset, chunk.offset + chunk.length))
    }

    @Test
    fun specWireSkipsTheSendAheadField() {
        val payload = byteArrayOf(9, 8, 7)
        listOf(0L, 12_345L, 0xFFFF_FFFFL).forEach { sendAhead ->
            val bytes = body(0x0102030405060708L, payload, sendAhead)
            val chunk = assertIs<BinaryFrame.Audio>(BinaryFrames.parse(WireVersion.SPEC_1_0, 4, bytes)).chunk
            assertEquals(0x0102030405060708L, chunk.timestampMicros, "send_ahead $sendAhead")
            assertEquals(BinaryFrames.TIMESTAMP_BYTES + BinaryFrames.SEND_AHEAD_BYTES, chunk.offset)
            assertContentEquals(payload, chunk.body.copyOfRange(chunk.offset, chunk.offset + chunk.length))
        }
    }

    @Test
    fun emptyPayloadIsValid() {
        val parsed = BinaryFrames.parse(WireVersion.LEGACY_9, 4, body(5, ByteArray(0)))
        assertIs<BinaryFrame.Audio>(parsed)
        assertEquals(0, parsed.chunk.length)
        assertEquals(
            0,
            assertIs<BinaryFrame.Audio>(
                BinaryFrames.parse(WireVersion.SPEC_1_0, 4, body(5, ByteArray(0), 0)),
            ).chunk.length,
        )
    }

    @Test
    fun nonAudioTypesAreReportedNotParsed() {
        assertEquals(BinaryFrame.Other(5), BinaryFrames.parse(WireVersion.LEGACY_9, 5, body(1, byteArrayOf(1))))
        assertEquals(BinaryFrame.Other(7), BinaryFrames.parse(WireVersion.SPEC_1_0, 7, byteArrayOf()))
    }

    @Test
    fun shortOrNegativeTimestampBodiesAreMalformed() {
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(WireVersion.LEGACY_9, 4, ByteArray(7)))
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(WireVersion.LEGACY_9, 4, body(-1L, byteArrayOf(1))))
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(WireVersion.SPEC_1_0, 4, ByteArray(11)))
        assertEquals(BinaryFrame.Malformed, BinaryFrames.parse(WireVersion.SPEC_1_0, 4, body(-1L, byteArrayOf(1), 0)))
    }
}
