package io.music_assistant.client.player.local

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.MediaPlayerListener
import io.music_assistant.client.player.PlatformAudioPlayer
import io.music_assistant.client.player.PlatformPlayerProvider
import io.music_assistant.client.player.RemoteCommandHandler
import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle
import io.music_assistant.sendspin.api.SinkPosition
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * [AudioSink] over the Swift `NativeAudioController` (AudioQueue). The native
 * player decodes FLAC and Opus itself, so it receives the stream's codec and
 * header and the Kotlin decoders pass encoded bytes through.
 *
 * No position feedback: iOS sync is open loop and the user's manual lag is the
 * only correction, by decision. Control Center commands are forwarded to
 * [onRemoteCommand]; interruptions become sink events.
 */
class AudioQueueSink(private val onRemoteCommand: (command: String) -> Unit) : AudioSink {
    private val logger = Logger.withTag("AudioQueueSink")

    override fun open(format: SinkFormat): SinkHandle {
        val player = PlatformPlayerProvider.player ?: error("no PlatformAudioPlayer registered")
        return Handle(player, format)
    }

    private inner class Handle(private val player: PlatformAudioPlayer, format: SinkFormat) : SinkHandle {
        private val sinkEvents = MutableSharedFlow<SinkEvent>(extraBufferCapacity = 8)
        override val events: Flow<SinkEvent> = sinkEvents
        override val latencyMicros: Long? = null

        init {
            player.prepareStream(
                codec = format.codec.name.lowercase(),
                sampleRate = format.sampleRate,
                channels = format.channels,
                bitDepth = format.bitDepth,
                codecHeader = format.codecHeaderBase64,
                listener = object : MediaPlayerListener {
                    override fun onReady() = Unit
                    override fun onAudioCompleted() = Unit
                    override fun onError(error: Throwable?) {
                        logger.e(error) { "Native player error" }
                        sinkEvents.tryEmit(SinkEvent.Died)
                    }
                },
            )
            player.setRemoteCommandHandler(
                object : RemoteCommandHandler {
                    override fun onCommand(command: String, source: String) {
                        when {
                            source == "remote" -> onRemoteCommand(command)
                            command == "pause" -> sinkEvents.tryEmit(SinkEvent.FocusLost)
                            command == "play" -> sinkEvents.tryEmit(SinkEvent.FocusRegained)
                        }
                    }
                },
            )
        }

        @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
        override fun write(pcm: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val data = pcm.usePinned { NSData.create(bytes = it.addressOf(offset), length = length.toULong()) }
            player.writeRawPcmNSData(data)
            return length
        }

        override fun pause() = player.pauseSink()
        override fun resume() = player.resumeSink()
        override fun flush() = player.flush()
        override fun position(): SinkPosition? = null
        override fun underrunCount(): Int = 0

        override fun close() {
            player.setRemoteCommandHandler(null)
            player.stopRawPcmStream()
        }
    }
}

/** Pass-through decoders: the native player decodes. */
class IosDecoderFactory : DecoderFactory {
    override fun supports(codec: AudioCodec): Boolean = true

    override fun create(codec: AudioCodec): AudioDecoder = PassThroughDecoder(codec)
}

private class PassThroughDecoder(override val outputCodec: AudioCodec) : AudioDecoder {
    override var outputBitDepth: Int = 16
        private set

    override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
        outputBitDepth = format.bitDepth
    }

    override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray =
        if (offset == 0 && length == input.size) input else input.copyOfRange(offset, offset + length)

    override fun reset() = Unit
    override fun release() = Unit
}
