package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamLifecycleTest {
    private val flac = StreamStartPlayer("flac", 48000, 2, 16)
    private val opus = StreamStartPlayer("opus", 48000, 2, 16)

    @Test
    fun startTable() {
        assertEquals(StreamAction.StartFresh(flac), StreamLifecycle.onStart(StreamPhase.Idle, null, flac, newConnection = false))
        assertEquals(StreamAction.StartFresh(flac), StreamLifecycle.onStart(StreamPhase.Ended, flac, flac, newConnection = true))
        // Same format on the same connection while playing is a seek or skip.
        assertEquals(StreamAction.StartFresh(flac), StreamLifecycle.onStart(StreamPhase.Playing, flac, flac, newConnection = false))
        // Same format on a new connection while playing is a reconnect resume.
        assertEquals(StreamAction.ResumeKeepBuffer, StreamLifecycle.onStart(StreamPhase.Playing, flac, flac, newConnection = true))
        // A format change is always fresh.
        assertEquals(StreamAction.StartFresh(opus), StreamLifecycle.onStart(StreamPhase.Playing, flac, opus, newConnection = true))
    }

    @Test
    fun endAndClearTable() {
        assertEquals(StreamAction.End, StreamLifecycle.onEnd(StreamPhase.Playing))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onEnd(StreamPhase.Idle))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onEnd(StreamPhase.Ended))
        assertEquals(StreamAction.Clear, StreamLifecycle.onClear(StreamPhase.Playing))
        assertEquals(StreamAction.Clear, StreamLifecycle.onClear(StreamPhase.Ended))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onClear(StreamPhase.Idle))
    }
}
