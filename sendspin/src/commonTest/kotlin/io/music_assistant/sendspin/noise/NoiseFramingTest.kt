package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.api.WireVersion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoiseFramingTest {
    private fun roundTrip(wire: WireVersion, type: Int, payload: ByteArray): NoiseFraming.Message {
        val frames = NoiseFraming.encode(wire, type, payload)
        frames.forEach { assertTrue(it.size <= NoiseFraming.MAX_FRAME_PLAINTEXT) }
        val decoder = NoiseFraming.Decoder(wire)
        var result: NoiseFraming.Message? = null
        frames.forEachIndexed { index, frame ->
            val decoded = decoder.decode(frame)
            if (index < frames.size - 1) assertNull(decoded) else result = decoded
        }
        return result!!
    }

    private fun legacyDecoder() = NoiseFraming.Decoder(WireVersion.LEGACY_9)

    private fun specDecoder() = NoiseFraming.Decoder(WireVersion.SPEC_1_0)

    @Test
    fun smallJsonMessageUsesSingleFrameOnBothWires() {
        val payload = """{"type":"server/hello"}""".encodeToByteArray()
        WireVersion.entries.forEach { wire ->
            val frames = NoiseFraming.encode(wire, NoiseFraming.TYPE_JSON, payload)
            assertEquals(1, frames.size)
            assertEquals(0, frames[0][0].toInt())
            assertContentEquals(payload, frames[0].copyOfRange(1, frames[0].size))
            val message = roundTrip(wire, NoiseFraming.TYPE_JSON, payload)
            assertEquals(NoiseFraming.TYPE_JSON, message.type)
            assertContentEquals(payload, message.payload)
        }
    }

    @Test
    fun payloadAtLimitStaysUnfragmented() {
        val payload = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD) { it.toByte() }
        WireVersion.entries.forEach { wire ->
            assertEquals(1, NoiseFraming.encode(wire, NoiseFraming.TYPE_JSON, payload).size)
        }
    }

    @Test
    fun legacyOversizedJsonPayloadRoundTripsThroughFragments() {
        val payload = Random(42).nextBytes(200_000)
        val frames = NoiseFraming.encode(WireVersion.LEGACY_9, NoiseFraming.TYPE_JSON, payload)
        assertTrue(frames.size > 1)
        assertEquals(NoiseFraming.TYPE_FRAGMENT_MORE, frames.first()[0].toInt())
        assertEquals(NoiseFraming.TYPE_JSON, frames.first()[1].toInt())
        assertEquals(NoiseFraming.TYPE_FRAGMENT_END, frames.last()[0].toInt())
        val message = roundTrip(WireVersion.LEGACY_9, NoiseFraming.TYPE_JSON, payload)
        assertEquals(NoiseFraming.TYPE_JSON, message.type)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun specOversizedJsonPayloadRoundTripsThroughFlaggedFragments() {
        val payload = Random(42).nextBytes(200_000)
        val frames = NoiseFraming.encode(WireVersion.SPEC_1_0, NoiseFraming.TYPE_JSON, payload)
        assertTrue(frames.size > 2)
        frames.forEach { assertEquals(NoiseFraming.TYPE_FRAGMENT, it[0].toInt()) }
        assertEquals(NoiseFraming.FLAG_FIRST, frames.first()[1].toInt())
        assertEquals(NoiseFraming.TYPE_JSON, frames.first()[2].toInt())
        frames.subList(1, frames.size - 1).forEach { assertEquals(0, it[1].toInt()) }
        assertEquals(NoiseFraming.FLAG_LAST, frames.last()[1].toInt())
        val message = roundTrip(WireVersion.SPEC_1_0, NoiseFraming.TYPE_JSON, payload)
        assertEquals(NoiseFraming.TYPE_JSON, message.type)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun oversizedAudioPayloadRestoresFrameBytesExactly() {
        val payload = Random(7).nextBytes(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 1)
        WireVersion.entries.forEach { wire ->
            val message = roundTrip(wire, 4, payload)
            assertEquals(4, message.type)
            val frameBytes = message.toFrameBytes()
            assertEquals(4, frameBytes[0].toInt())
            assertContentEquals(payload, frameBytes.copyOfRange(1, frameBytes.size))
        }
    }

    @Test
    fun oneByteOverLimitFragmentsIntoTwoFrames() {
        val payload = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 1)
        WireVersion.entries.forEach { wire ->
            val frames = NoiseFraming.encode(wire, NoiseFraming.TYPE_JSON, payload)
            assertEquals(2, frames.size, "$wire")
            assertContentEquals(payload, roundTrip(wire, NoiseFraming.TYPE_JSON, payload).payload)
        }
    }

    @Test
    fun specSingleFrameWithFirstAndLastFlagsCompletesAtOnce() {
        val flags = (NoiseFraming.FLAG_FIRST or NoiseFraming.FLAG_LAST).toByte()
        val message = specDecoder().decode(byteArrayOf(1, flags, 4, 7, 8))!!
        assertEquals(4, message.type)
        assertContentEquals(byteArrayOf(7, 8), message.payload)
    }

    @Test
    fun legacyFragmentEndWithNothingInFlightIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> { legacyDecoder().decode(byteArrayOf(3, 1, 2, 3)) }
    }

    @Test
    fun specContinuationWithNothingInFlightIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> { specDecoder().decode(byteArrayOf(1, 0, 1, 2)) }
        assertFailsWith<NoiseFraming.ProtocolException> {
            specDecoder().decode(byteArrayOf(1, NoiseFraming.FLAG_LAST.toByte(), 1, 2))
        }
    }

    @Test
    fun specFirstFragmentWhileInFlightIsProtocolError() {
        val decoder = specDecoder()
        assertNull(decoder.decode(byteArrayOf(1, NoiseFraming.FLAG_FIRST.toByte(), 0, 9)))
        assertFailsWith<NoiseFraming.ProtocolException> {
            decoder.decode(byteArrayOf(1, NoiseFraming.FLAG_FIRST.toByte(), 0, 9))
        }
    }

    @Test
    fun nonFragmentFrameWhileFragmentInFlightIsProtocolError() {
        val legacy = legacyDecoder()
        assertNull(legacy.decode(byteArrayOf(2, 0, 9, 9)))
        assertFailsWith<NoiseFraming.ProtocolException> { legacy.decode(byteArrayOf(0, 1, 2)) }

        val spec = specDecoder()
        assertNull(spec.decode(byteArrayOf(1, NoiseFraming.FLAG_FIRST.toByte(), 0, 9)))
        assertFailsWith<NoiseFraming.ProtocolException> { spec.decode(byteArrayOf(0, 1, 2)) }
    }

    @Test
    fun specReservedFlagBitsAreProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> { specDecoder().decode(byteArrayOf(1, 0x04, 0, 9)) }
        assertFailsWith<NoiseFraming.ProtocolException> { specDecoder().decode(byteArrayOf(1, 0x80.toByte(), 0, 9)) }
    }

    @Test
    fun fragmentOrigTypeCannotBeAFragmentType() {
        assertFailsWith<NoiseFraming.ProtocolException> { legacyDecoder().decode(byteArrayOf(2, 3, 9)) }
        assertFailsWith<NoiseFraming.ProtocolException> {
            specDecoder().decode(byteArrayOf(1, NoiseFraming.FLAG_FIRST.toByte(), 1, 9))
        }
    }

    @Test
    fun emptyFramePlaintextIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> { legacyDecoder().decode(ByteArray(0)) }
        assertFailsWith<NoiseFraming.ProtocolException> { specDecoder().decode(ByteArray(0)) }
    }

    @Test
    fun openingFragmentMissingHeaderBytesIsProtocolError() {
        assertFailsWith<NoiseFraming.ProtocolException> { legacyDecoder().decode(byteArrayOf(2)) }
        assertFailsWith<NoiseFraming.ProtocolException> { specDecoder().decode(byteArrayOf(1)) }
        assertFailsWith<NoiseFraming.ProtocolException> {
            specDecoder().decode(byteArrayOf(1, NoiseFraming.FLAG_FIRST.toByte()))
        }
    }

    @Test
    fun theOtherWiresFragmentTypesDecodeAsPlainMessages() {
        val onSpec = specDecoder().decode(byteArrayOf(2, 0, 9))!!
        assertEquals(NoiseFraming.TYPE_FRAGMENT_MORE, onSpec.type)
        assertContentEquals(byteArrayOf(0, 9), onSpec.payload)
        val onLegacy = legacyDecoder().decode(byteArrayOf(1, 0x02, 0, 9))!!
        assertEquals(NoiseFraming.TYPE_FRAGMENT, onLegacy.type)
    }

    @Test
    fun encoderRejectsItsOwnFragmentTypesAsApplicationTypes() {
        assertFailsWith<IllegalArgumentException> {
            NoiseFraming.encode(WireVersion.LEGACY_9, NoiseFraming.TYPE_FRAGMENT_MORE, ByteArray(1))
        }
        assertFailsWith<IllegalArgumentException> {
            NoiseFraming.encode(WireVersion.LEGACY_9, NoiseFraming.TYPE_FRAGMENT_END, ByteArray(1))
        }
        assertFailsWith<IllegalArgumentException> {
            NoiseFraming.encode(WireVersion.SPEC_1_0, NoiseFraming.TYPE_FRAGMENT, ByteArray(1))
        }
        assertEquals(1, NoiseFraming.encode(WireVersion.SPEC_1_0, NoiseFraming.TYPE_FRAGMENT_MORE, ByteArray(1)).size)
        assertEquals(1, NoiseFraming.encode(WireVersion.LEGACY_9, NoiseFraming.TYPE_FRAGMENT, ByteArray(1)).size)
    }

    @Test
    fun decoderIsReusableAcrossMessages() {
        WireVersion.entries.forEach { wire ->
            val decoder = NoiseFraming.Decoder(wire)
            val big = Random(1).nextBytes(70_000)
            var last: NoiseFraming.Message? = null
            NoiseFraming.encode(wire, NoiseFraming.TYPE_JSON, big).forEach { last = decoder.decode(it) }
            assertContentEquals(big, last!!.payload)
            val small = decoder.decode(byteArrayOf(4, 42))!!
            assertEquals(4, small.type)
            assertContentEquals(byteArrayOf(42), small.payload)
        }
    }
}
