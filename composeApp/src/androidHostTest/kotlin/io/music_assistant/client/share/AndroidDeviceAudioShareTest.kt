package io.music_assistant.client.share

import android.app.Service
import io.music_assistant.sendspin.api.AudioCapture
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDeviceAudioShareTest {
    private class Capture : AudioCapture {
        override val format = CaptureFormat(sampleRate = 48_000, channels = 2, bitDepth = 16)
        override fun frames(): Flow<CapturedAudio> = emptyFlow()
    }

    private val share = AndroidDeviceAudioShare(RuntimeEnvironment.getApplication(), Service::class.java)

    @Test
    fun aRevokeStaysASystemEndWhenTheServiceIsDestroyedAfter() {
        share.onStarted(Capture())
        share.onRevoked() // MediaProjection.Callback.onStop
        share.onRevoked() // Service.onDestroy

        assertNull(share.capture.value)
        assertTrue(share.endedBySystem)
    }

    @Test
    fun aStopByTheUserIsNoSystemEnd() {
        share.onStarted(Capture())
        share.stop()
        share.onRevoked() // Service.onDestroy

        assertFalse(share.endedBySystem)
    }

    @Test
    fun aFailedCaptureEndsTheShareAsASystemEnd() {
        val capture = Capture()
        share.onStarted(capture)
        share.onCaptureFailed(capture)

        assertNull(share.capture.value)
        assertTrue(share.endedBySystem)
    }

    @Test
    fun theFailureOfAnOldCaptureHasNoEffect() {
        val old = Capture()
        val current = Capture()
        share.onStarted(old)
        share.stop()
        share.onStarted(current)
        share.onCaptureFailed(old)

        assertSame(current, share.capture.value)
        assertFalse(share.endedBySystem)
    }
}
