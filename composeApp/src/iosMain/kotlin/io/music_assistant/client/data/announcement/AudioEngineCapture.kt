package io.music_assistant.client.data.announcement

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.setActive
import platform.Foundation.NSError

/**
 * [MicrophoneCapture] over an [AVAudioEngine] input tap, in the hardware's own rate.
 *
 * Recording needs the play-and-record category, but the rest of the app owns the session as
 * playback. So the session's category, mode and options are saved before and restored exactly
 * after: `NowPlayingCoordinator` caches what it last set and skips a set it thinks is current.
 * The built-in microphone is used, and output stays on the speaker or a Bluetooth A2DP device,
 * so music that plays here keeps playing.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class AudioEngineCapture : MicrophoneCapture {
    override fun frames(): Flow<PcmFrame> = callbackFlow {
        val session = AVAudioSession.sharedInstance()
        // Without the grant the input node has no format, and the tap would raise an ObjC exception.
        check(session.recordPermission == AVAudioSessionRecordPermissionGranted) { "No microphone permission" }
        val saved = Triple(session.category, session.mode, session.categoryOptions)
        val engine = AVAudioEngine()
        val input = engine.inputNode
        try {
            attempt {
                session.setCategory(
                    AVAudioSessionCategoryPlayAndRecord,
                    AVAudioSessionModeDefault,
                    RECORD_OPTIONS,
                    it,
                )
            }
            attempt { session.setActive(true, it) }
            val format = input.outputFormatForBus(0u)
            val sampleRate = format.sampleRate.toInt()
            check(sampleRate > 0 && format.channelCount > 0u) { "Microphone has no input format" }
            input.installTapOnBus(0u, (sampleRate / TAPS_PER_SECOND).toUInt(), format) { buffer, _ ->
                buffer?.toS16le()?.let { trySend(PcmFrame(sampleRate, it)) }
            }
            engine.prepare()
            attempt { engine.startAndReturnError(it) }
        } catch (e: Exception) {
            input.removeTapOnBus(0u)
            restore(session, saved)
            throw e
        }
        awaitClose {
            input.removeTapOnBus(0u)
            engine.stop()
            restore(session, saved)
        }
    }.buffer(Channel.UNLIMITED)

    private fun restore(session: AVAudioSession, saved: Triple<String?, String?, ULong>) {
        runCatching { attempt { session.setCategory(saved.first, saved.second, saved.third, it) } }
    }

    /** Runs an NSError-reporting call and throws when it fails. */
    private fun attempt(call: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> Boolean) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        check(call(error.ptr)) { error.value?.localizedDescription ?: "Audio session call failed" }
    }

    /** Channel 0 only: the announcement is mono. */
    private fun AVAudioPCMBuffer.toS16le(): ByteArray? {
        val samples = floatChannelData?.get(0) ?: return null
        val count = frameLength.toInt()
        val out = ByteArray(count * Short.SIZE_BYTES)
        for (i in 0 until count) {
            val value = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            out[Short.SIZE_BYTES * i] = value.toByte()
            out[Short.SIZE_BYTES * i + 1] = (value shr Byte.SIZE_BITS).toByte()
        }
        return out
    }

    private companion object {
        val RECORD_OPTIONS = AVAudioSessionCategoryOptionDefaultToSpeaker or
            AVAudioSessionCategoryOptionAllowBluetoothA2DP or
            AVAudioSessionCategoryOptionMixWithOthers
        const val TAPS_PER_SECOND = 50 // a hint: iOS may deliver larger buffers
    }
}
