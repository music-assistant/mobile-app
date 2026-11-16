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

/** The default and highest advertised rate: Opus runs at 48 kHz only. */
internal const val ADVERTISED_SAMPLE_RATE = 48_000

/** The CD rate, advertised for the lossless codecs so a server can send 44.1 kHz audio without resampling it. */
internal const val CD_SAMPLE_RATE = 44_100
internal const val ADVERTISED_CHANNELS = 2

/** About 96 kbit/s (the libopus default for stereo) plus chunk headers. */
private const val OPUS_BYTES_PER_SECOND = 16_000

/**
 * Wire bytes per second of [codec] at [bitDepth] at the highest advertised rate, as a
 * buffer bound: FLAC is counted as raw PCM, its worst case. Opus is always 16-bit.
 */
public fun estimatedBytesPerSecond(codec: AudioCodec, bitDepth: Int): Int = when (codec) {
    AudioCodec.OPUS -> OPUS_BYTES_PER_SECOND
    AudioCodec.FLAC, AudioCodec.PCM -> ADVERTISED_SAMPLE_RATE * ADVERTISED_CHANNELS * bitDepth / 8
}

/**
 * `supported_formats` in preference order: each preferred codec at [bitDepth]
 * (24-bit then 16-bit for the lossless codecs) at 48 kHz, then a lossless codec
 * again at 44.1 kHz, then 16-bit PCM at 48 kHz, the format every spec server must
 * produce. 48 kHz stays first, because a server that does not match the source
 * rate sends the first format.
 */
internal fun advertisedFormats(
    codecs: List<AudioCodec>,
    bitDepth: Int,
    supports: (AudioCodec) -> Boolean,
): List<AudioFormatSpec> {
    fun spec(codec: AudioCodec, rate: Int, depth: Int) = AudioFormatSpec(codec, ADVERTISED_CHANNELS, rate, depth)
    val preferred = codecs.filter(supports).flatMap { codec ->
        val depths = if (codec != AudioCodec.OPUS && bitDepth == 24) listOf(24, 16) else listOf(16)
        val rates = listOfNotNull(ADVERTISED_SAMPLE_RATE, CD_SAMPLE_RATE.takeIf { codec != AudioCodec.OPUS })
        rates.flatMap { rate -> depths.map { spec(codec, rate, it) } }
    }
    val guaranteed = spec(AudioCodec.PCM, ADVERTISED_SAMPLE_RATE, 16).takeIf { supports(AudioCodec.PCM) }
    return (preferred + listOfNotNull(guaranteed)).distinct()
}

/** `buffer_capacity`: [seconds] of the heaviest advertised format, so a fallback format still gets them. */
internal fun bufferCapacityBytes(formats: List<AudioFormatSpec>, seconds: Int): Int =
    seconds * (formats.maxOfOrNull { estimatedBytesPerSecond(it.codec, it.bitDepth) } ?: 0)
