package io.music_assistant.sendspin.api

import kotlinx.coroutines.flow.Flow

/**
 * An audio input of the device, for the `source@v1` role. The player collects
 * [frames] only while the server wants the source to stream.
 */
public interface AudioCapture {
    public val format: CaptureFormat

    /**
     * Cold: a collection is one recording, and cancelling it frees the input.
     * A failure of the input ends the flow, with or without an exception.
     */
    public fun frames(): Flow<CapturedAudio>
}

/** Interleaved little-endian signed PCM. */
public data class CaptureFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
) {
    val bytesPerFrame: Int get() = channels * bitDepth / Byte.SIZE_BITS
}

/** Whole frames of PCM. [captureTimeMicros] is when the first sample reached the input, on [SendspinDeps.clock]. */
public class CapturedAudio(
    public val pcm: ByteArray,
    public val captureTimeMicros: Long,
)

/**
 * What the player offers as a source. A change of [capture] or [dialect]
 * restarts the connection, because the roles travel in `client/hello`.
 */
public data class SourceConfig(
    val capture: AudioCapture,
    val dialect: SourceDialect = SourceDialect.SPEC,
)

/** The revision of the source role that the server speaks. The handshake does not tell, so the app selects it. */
public enum class SourceDialect {
    /** The current spec: no source object in `client/hello`, and a start needs the `playback` activity. */
    SPEC,

    /** Spec 1.0.0-rc1 (aiosendspin 10.3 and older): a source object in `client/hello`, and a start needs no activity. */
    RC1,
}

public enum class SourceStatus {
    /** No source is offered, or the server has not answered yet. */
    Off,

    /** The server activated the connection without the source role, on a paired session. */
    Refused,

    /** The server accepted the source. It streams when the server asks. */
    Ready,

    Streaming,
}
