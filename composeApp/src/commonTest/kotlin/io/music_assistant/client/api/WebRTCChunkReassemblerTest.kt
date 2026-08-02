package io.music_assistant.client.api

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebRTCChunkReassemblerTest {
    private fun chunks(text: String, id: Int, pieceSize: Int): List<String> {
        val pieces = text.encodeToByteArray().asList().chunked(pieceSize)
        return pieces.mapIndexed { seq, piece ->
            chunkFrame(id, seq, pieces.size, Base64.encode(piece.toByteArray()))
        }
    }

    private fun chunkFrame(id: Int, seq: Int, count: Int, b64: String): String =
        """{"type":"__chunk__","id":$id,"seq":$seq,"count":$count,"b64":"$b64"}"""

    @Test
    fun passesLegacyWholeMessagesThroughUnchanged() {
        val message = """{"event":"player_updated"}"""
        assertEquals(message, WebRTCChunkReassembler().accept(message))
    }

    @Test
    fun boundedProbeDoesNotMisclassifyTokenLateInNormalMessage() {
        val message = """{"data":"${"x".repeat(300)}__chunk__"}"""
        assertEquals(message, WebRTCChunkReassembler().accept(message))
    }

    @Test
    fun reassemblesOrderedChunks() {
        val message = """{"message_id":"1","result":[1,2,3]}"""
        val results = chunks(message, id = 42, pieceSize = 8).map(WebRTCChunkReassembler()::accept)
        assertTrue(results.dropLast(1).all { it == null })
        assertEquals(message, results.last())
    }

    @Test
    fun reassemblesOutOfOrderMultibyteChunksByByteSequence() {
        val message = """{"name":"${"音楽ライブラリ".repeat(5)}"}"""
        val results = chunks(message, id = 7, pieceSize = 5).reversed()
            .map(WebRTCChunkReassembler()::accept)
        assertTrue(results.dropLast(1).all { it == null })
        assertEquals(message, results.last())
    }

    @Test
    fun reassemblesInterleavedGroupsIndependently() {
        val first = chunks("""{"event":"first"}""", id = 1, pieceSize = 5)
        val second = chunks("""{"event":"second"}""", id = 2, pieceSize = 5)
        val reassembler = WebRTCChunkReassembler()
        val results = buildList {
            repeat(maxOf(first.size, second.size)) { index ->
                first.getOrNull(index)?.let { add(reassembler.accept(it)) }
                second.getOrNull(index)?.let { add(reassembler.accept(it)) }
            }
        }.filterNotNull()
        assertEquals(listOf("""{"event":"first"}""", """{"event":"second"}"""), results)
    }

    @Test
    fun duplicateChunkDoesNotCompleteGroupEarly() {
        val message = """{"event":"queue_updated","data":"abcdefgh"}"""
        val frames = chunks(message, id = 9, pieceSize = 10)
        val reassembler = WebRTCChunkReassembler()
        assertNull(reassembler.accept(frames.first()))
        assertNull(reassembler.accept(frames.first()))
        assertEquals(message, frames.drop(1).map(reassembler::accept).last())
    }

    @Test
    fun rejectsOversizedCountWithoutAllocating() {
        val frame = chunkFrame(id = 1, seq = 0, count = Int.MAX_VALUE, b64 = "eA==")
        assertNull(WebRTCChunkReassembler().accept(frame))
    }

    @Test
    fun countMismatchDoesNotDiscardOriginalGroup() {
        val message = """{"event":"healthy"}"""
        val frames = chunks(message, id = 3, pieceSize = 10)
        val reassembler = WebRTCChunkReassembler()
        assertNull(reassembler.accept(frames.first()))
        assertNull(reassembler.accept(chunkFrame(id = 3, seq = 0, count = frames.size + 1, b64 = "eA==")))
        assertEquals(message, frames.drop(1).map(reassembler::accept).last())
    }

    @Test
    fun malformedChunkIsConsumedAndSubsequentGroupStillWorks() {
        val reassembler = WebRTCChunkReassembler()
        assertNull(reassembler.accept(chunkFrame(id = 1, seq = 99, count = 2, b64 = "eA==")))
        val message = """{"event":"healthy"}"""
        assertEquals(message, chunks(message, id = 2, pieceSize = 100).single().let(reassembler::accept))
    }

    @Test
    fun invalidUtf8IsConsumedWithoutThrowing() {
        val invalidUtf8 = Base64.encode(byteArrayOf(0xC3.toByte(), 0x28))
        assertNull(WebRTCChunkReassembler().accept(chunkFrame(id = 4, seq = 0, count = 1, b64 = invalidUtf8)))
    }

    @Test
    fun oldestIncompleteGroupIsEvictedAtCapacity() {
        val reassembler = WebRTCChunkReassembler()
        val first = chunks("""{"event":"first"}""", id = 0, pieceSize = 5)
        assertNull(reassembler.accept(first.first()))
        repeat(MAX_PENDING_CHUNK_GROUPS) { id ->
            assertNull(reassembler.accept(chunkFrame(id = id + 1, seq = 0, count = 2, b64 = "eA==")))
        }
        assertTrue(first.drop(1).map(reassembler::accept).all { it == null })
    }
}
