package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle
import io.music_assistant.sendspin.api.SinkPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.min

/**
 * A sink whose device plays at exactly the nominal rate from the first write,
 * with scripted position feedback and latency. Writes never block.
 */
class FakeSink(private val nowMicros: () -> Long) : AudioSink {
    val handles = mutableListOf<Handle>()
    var reportPosition = true
    var latencyMicros: Long? = 0L

    /** Runs inside [open], before the handle exists: simulates a slow device build. */
    var onOpen: (() -> Unit)? = null

    override fun open(format: SinkFormat): Handle {
        onOpen?.invoke()
        return Handle(format).also { handles += it }
    }

    inner class Handle(val format: SinkFormat) : SinkHandle {
        val writes = mutableListOf<ByteArray>()
        var paused = false
        var flushes = 0
        var closed = false
        var dead = false
        private var framesWritten = 0L
        private var firstWriteMicros: Long? = null
        private val sinkEvents = MutableSharedFlow<SinkEvent>(extraBufferCapacity = 4)

        override val events: Flow<SinkEvent> = sinkEvents
        override val latencyMicros: Long? get() = this@FakeSink.latencyMicros

        /** Live collectors of [events]; must drop to zero once the handle is replaced. */
        val subscribers: Int get() = sinkEvents.subscriptionCount.value

        val totalBytes: Int get() = writes.sumOf { it.size }

        fun framesPlayed(): Long {
            val start = firstWriteMicros ?: return 0L
            val elapsedFrames = (nowMicros() - start) * format.sampleRate / 1_000_000L
            return min(framesWritten, elapsedFrames)
        }

        override fun write(pcm: ByteArray, offset: Int, length: Int): Int {
            if (dead) return -1
            writes += pcm.copyOfRange(offset, offset + length)
            framesWritten += length / format.bytesPerFrame
            if (firstWriteMicros == null) firstWriteMicros = nowMicros()
            return length
        }

        override fun pause() {
            paused = true
        }

        override fun resume() {
            paused = false
        }

        override fun flush() {
            flushes++
            framesWritten = 0
            firstWriteMicros = null
        }

        override fun position(): SinkPosition? =
            if (reportPosition) SinkPosition(framesPlayed(), nowMicros()) else null

        override fun underrunCount(): Int = 0

        override fun close() {
            closed = true
        }

        fun emit(event: SinkEvent) {
            sinkEvents.tryEmit(event)
        }
    }
}
