package io.music_assistant.sendspin.noise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoiseFramingTest {
    private fun roundTrip(type: Int, payload: ByteArray): NoiseFraming.Message {
        val frames = NoiseFraming.encode(type, payload)
        frames.forEach { assertTrue(it.size <= NoiseFraming.MAX_FRAME_PLAINTEXT) }
        val decoder = NoiseFraming.Decoder()
        var result: NoiseFraming.Message? = null
        frames.forEachIndexed { index, frame ->
            val decoded = decoder.decode(frame)
            if (index < frames.size - 1) assertNull(decoded) else result = decoded
        }
        return result!!
    }

    @Test
    fun smallJsonMessageUsesSingleFrame() {
        val payload = """{"type":"server/hello"}""".encodeToByteArray()
        val frames = NoiseFraming.encode(NoiseFraming.TYPE_JSON, payload)
        assertEquals(1, frames.size)
        assertEquals(0, frames[0][0].toInt())
        assertContentEquals(payload, frames[0].copyOfRange(1, frames[0].size))
        val message = roundTrip(NoiseFraming.TYPE_JSON, payload)
        assertEquals(NoiseFraming.TYPE_JSON, message.type)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun payloadAtLimitStaysUnfragmented() {
        val payload = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD) { it.toByte() }
        assertEquals(1, NoiseFraming.encode(NoiseFraming.TYPE_JSON, payload).size)
    }

    @Test
    fun oversizedJsonPayloadRoundTripsThroughFragments() {
        val payload = Random(42).nextBytes(200_000)
        val frames = NoiseFraming.encode(NoiseFraming.TYPE_JSON, payload)
        assertTrue(frames.size > 1)
        assertEquals(NoiseFraming.TYPE_FRAGMENT_MORE, frames.first()[0].toInt())
        assertEquals(NoiseFraming.TYPE_JSON, frames.first()[1].toInt())
        assertEquals(NoiseFraming.TYPE_FRAGMENT_END, frames.last()[0].toInt())
        val message = roundTrip(NoiseFraming.TYPE_JSON, payload)
        assertEquals(NoiseFraming.TYPE_JSON, message.type)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun oversizedAudioPayloadRestoresFrameBytesExactly() {
        val payload = Random(7).nextBytes(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 1)
        val message = roundTrip(4, payload)
        assertEquals(4, message.type)
        val frameBytes = message.toFrameBytes()
        assertEquals(4, frameBytes[0].toInt())
        assertContentEquals(payload, frameBytes.copyOfRange(1, frameBytes.size))
    }

    @Test
    fun oneByteOverLimitFragmentsIntoTwoFrames() {
        val payload = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 1)
        val frames = NoiseFraming.encode(NoiseFraming.TYPE_JSON, payload)
        assertEquals(2, frames.size)
        assertContentEquals(payload, roundTrip(NoiseFraming.TYPE_JSON, payload).payload)
    }

    @Test
    fun fragmentEndWithNothingInFlightIsProtocolError() {
        val decoder = NoiseFraming.Decoder()
        assertFailsWith<NoiseFraming.ProtocolException> {
            decoder.decode(byteArrayOf(3, 1, 2, 3))
        }
    }

    @Test
    fun nonFragmentFrameWhileFragmentInFlightIsProtocolError() {
        val decoder = NoiseFraming.Decoder()
        assertNull(decoder.decode(byteArrayOf(2, 0, 9, 9)))
        assertFailsWith<NoiseFraming.ProtocolException> {
            decoder.decode(byteArrayOf(0, 1, 2))
        }
    }

    @Test
    fun fragmentOrigTypeCannotBeAFragmentType() {
        val decoder = NoiseFraming.Decoder()
        assertFailsWith<NoiseFraming.ProtocolException> {
            decoder.decode(byteArrayOf(2, 3, 9))
        }
    }

    @Test
    fun emptyFramePlaintextIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> {
            NoiseFraming.Decoder().decode(ByteArray(0))
        }
    }

    @Test
    fun openingFragmentMissingOrigTypeIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> {
            NoiseFraming.Decoder().decode(byteArrayOf(2))
        }
    }

    @Test
    fun encoderRejectsFragmentTypesAsApplicationTypes() {
        assertFailsWith<IllegalArgumentException> {
            NoiseFraming.encode(NoiseFraming.TYPE_FRAGMENT_MORE, ByteArray(1))
        }
        assertFailsWith<IllegalArgumentException> {
            NoiseFraming.encode(NoiseFraming.TYPE_FRAGMENT_END, ByteArray(1))
        }
    }

    @Test
    fun decoderIsReusableAcrossMessages() {
        val decoder = NoiseFraming.Decoder()
        val big = Random(1).nextBytes(70_000)
        var last: NoiseFraming.Message? = null
        NoiseFraming.encode(NoiseFraming.TYPE_JSON, big).forEach { last = decoder.decode(it) }
        assertContentEquals(big, last!!.payload)
        val small = decoder.decode(byteArrayOf(4, 42))!!
        assertEquals(4, small.type)
        assertContentEquals(byteArrayOf(42), small.payload)
    }
}
