package io.music_assistant.sendspin.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatToPcm24Test {
    private fun floats(values: List<Float>): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder()).apply { values.forEach(::putFloat) }.array()

    private fun samples(pcm: ByteArray): List<Int> = (0 until pcm.size / 3).map { i ->
        (pcm[i * 3].toInt() and 0xFF) or ((pcm[i * 3 + 1].toInt() and 0xFF) shl 8) or (pcm[i * 3 + 2].toInt() shl 16)
    }

    @Test
    fun twentyFourBitSamplesRoundTripExactly() {
        val ints = listOf(0, 1, -1, 8_388_607, -8_388_608, 123_456, -654_321)
        val pcm = floatToPcm24(floats(ints.map { it / 8_388_608f }))
        assertEquals(ints, samples(pcm))
    }

    @Test
    fun fullScaleAndOverRangeClamp() {
        assertEquals(listOf(8_388_607, -8_388_608, 8_388_607), samples(floatToPcm24(floats(listOf(1f, -1.5f, 2f)))))
    }
}
