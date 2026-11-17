package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Track

/** The item that the server analysed: the provider-native id of the stream and its provider. */
data class WaveformKey(val itemId: String, val provider: String)

/**
 * The waveform key of a track whose stream has started. Null for every other item, like in the
 * web frontend: the 1800 bins of a long item do not match a chapter timeline, and a radio has no end.
 */
val QueueTrack.waveformKey: WaveformKey?
    get() {
        if (track !is Track) return null
        val itemId = streamItemId ?: return null
        val provider = provider ?: return null
        return WaveformKey(itemId, provider)
    }
