// PCM bit-depth literals (16/24) and sample-rate bounds are audio-format standards;
// naming them `BIT_DEPTH_16` etc. doesn't add clarity over the literal.
@file:Suppress("MagicNumber")

package io.music_assistant.sendspin.api

public data class AudioFormatSpec(
    val codec: AudioCodec,
    val channels: Int,
    val sampleRate: Int,
    val bitDepth: Int,
) {
    init {
        require(channels in 1..32) { "Channels must be between 1 and 32" }
        require(sampleRate in 1..384_000) { "Sample rate must be between 1 and 384000 Hz" }
        require(bitDepth in setOf(16, 24)) { "Bit depth must be 16 or 24" }
    }
}

/** The one stream shape this client advertises: stereo at 48 kHz. */
internal const val ADVERTISED_SAMPLE_RATE = 48_000
internal const val ADVERTISED_CHANNELS = 2

/** About 96 kbit/s (the libopus default for stereo) plus chunk headers. */
private const val OPUS_BYTES_PER_SECOND = 16_000

/**
 * Wire bytes per second of [codec] at [bitDepth] in the advertised shape, as a
 * buffer bound: FLAC is counted as raw PCM, its worst case. Opus is always 16-bit.
 */
public fun estimatedBytesPerSecond(codec: AudioCodec, bitDepth: Int): Int = when (codec) {
    AudioCodec.OPUS -> OPUS_BYTES_PER_SECOND
    AudioCodec.FLAC, AudioCodec.PCM -> ADVERTISED_SAMPLE_RATE * ADVERTISED_CHANNELS * bitDepth / 8
}

/**
 * `supported_formats` in preference order: each preferred codec at [bitDepth]
 * (24-bit then 16-bit for the lossless codecs), then 16-bit PCM, the format every
 * spec server must produce.
 */
internal fun advertisedFormats(
    codecs: List<AudioCodec>,
    bitDepth: Int,
    supports: (AudioCodec) -> Boolean,
): List<AudioFormatSpec> {
    fun spec(codec: AudioCodec, depth: Int) = AudioFormatSpec(codec, ADVERTISED_CHANNELS, ADVERTISED_SAMPLE_RATE, depth)
    val preferred = codecs.filter(supports).flatMap { codec ->
        val depths = if (codec != AudioCodec.OPUS && bitDepth == 24) listOf(24, 16) else listOf(16)
        depths.map { spec(codec, it) }
    }
    return (preferred + listOfNotNull(spec(AudioCodec.PCM, 16).takeIf { supports(AudioCodec.PCM) })).distinct()
}

/** `buffer_capacity`: [seconds] of the heaviest advertised format, so a fallback format still gets them. */
internal fun bufferCapacityBytes(formats: List<AudioFormatSpec>, seconds: Int): Int =
    seconds * (formats.maxOfOrNull { estimatedBytesPerSecond(it.codec, it.bitDepth) } ?: 0)
