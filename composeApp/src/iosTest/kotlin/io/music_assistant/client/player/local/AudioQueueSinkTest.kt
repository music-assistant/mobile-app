package io.music_assistant.client.player.local

import io.music_assistant.client.player.MediaPlayerListener
import io.music_assistant.client.player.PlatformAudioPlayer
import io.music_assistant.client.player.RemoteCommandHandler
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.wire.AudioCodec
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The native player reports a failed `prepareStream` synchronously, before anyone can subscribe. */
class AudioQueueSinkTest {
    private val format = SinkFormat(48_000, 2, 16, AudioCodec.FLAC, null)

    @Test
    fun aSynchronousPrepareFailureFailsOpen() {
        val player = FakePlatformAudioPlayer(failPrepare = true)
        val sink = AudioQueueSink(onRemoteCommand = {}, playerProvider = { player })
        assertFailsWith<IllegalStateException> { sink.open(format) }
    }

    @Test
    fun aLaterNativeErrorFailsTheNextWriteEvenWithoutASubscriber() {
        val player = FakePlatformAudioPlayer(failPrepare = false)
        val sink = AudioQueueSink(onRemoteCommand = {}, playerProvider = { player })
        val handle = sink.open(format)
        assertEquals(4, handle.write(ByteArray(4), 0, 4))
        player.listener?.onError(IllegalStateException("decoder"))
        assertEquals(-1, handle.write(ByteArray(4), 0, 4))
    }
}

private class FakePlatformAudioPlayer(private val failPrepare: Boolean) : PlatformAudioPlayer {
    var listener: MediaPlayerListener? = null

    override fun prepareStream(
        codec: String,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        codecHeader: String?,
        listener: MediaPlayerListener,
    ) {
        this.listener = listener
        if (failPrepare) listener.onError(IllegalStateException("no decoder")) else listener.onReady()
    }

    override fun writeRawPcm(data: ByteArray) = Unit
    override fun writeRawPcmNSData(data: NSData) = Unit
    override fun pauseSink() = Unit
    override fun resumeSink() = Unit
    override fun flush() = Unit
    override fun stopRawPcmStream() = Unit
    override fun setVolume(volume: Int) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun dispose() = Unit
    override fun setLongFormSeekIntervals(backSeconds: Long, forwardSeconds: Long) = Unit
    override fun setRemoteCommandHandler(handler: RemoteCommandHandler?) = Unit
}
