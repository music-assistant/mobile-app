package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamLifecycleTest {
    private val flac = StreamStartPlayer("flac", 48000, 2, 16)
    private val opus = StreamStartPlayer("opus", 48000, 2, 16)

    @Test
    fun startTable() {
        assertEquals(
            StreamAction.StartFresh(flac),
            StreamLifecycle.onStart(StreamPhase.Idle, null, flac, newConnection = false),
        )
        assertEquals(
            StreamAction.StartFresh(flac),
            StreamLifecycle.onStart(StreamPhase.Ended, flac, flac, newConnection = true),
        )
        // A start on the same connection while playing updates the active stream in place.
        assertEquals(
            StreamAction.Continue(flac),
            StreamLifecycle.onStart(StreamPhase.Playing, flac, flac, newConnection = false),
        )
        assertEquals(
            StreamAction.Continue(opus),
            StreamLifecycle.onStart(StreamPhase.Playing, flac, opus, newConnection = false),
        )
        // Same format on a new connection while playing is a reconnect resume.
        assertEquals(
            StreamAction.ResumeKeepBuffer,
            StreamLifecycle.onStart(StreamPhase.Playing, flac, flac, newConnection = true),
        )
        // A format change on a new connection is fresh.
        assertEquals(
            StreamAction.StartFresh(opus),
            StreamLifecycle.onStart(StreamPhase.Playing, flac, opus, newConnection = true),
        )
    }

    @Test
    fun endAndClearTable() {
        assertEquals(StreamAction.End, StreamLifecycle.onEnd(StreamPhase.Playing))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onEnd(StreamPhase.Idle))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onEnd(StreamPhase.Ended))
        assertEquals(StreamAction.Clear, StreamLifecycle.onClear(StreamPhase.Playing, null))
        assertEquals(StreamAction.Clear, StreamLifecycle.onClear(StreamPhase.Ended, null))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onClear(StreamPhase.Idle, null))
        assertEquals(StreamAction.ClearFrom(5), StreamLifecycle.onClear(StreamPhase.Playing, 5))
        assertEquals(StreamAction.Ignore, StreamLifecycle.onClear(StreamPhase.Idle, 5))
    }
}
