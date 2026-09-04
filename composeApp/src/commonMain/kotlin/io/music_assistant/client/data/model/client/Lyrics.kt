package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.utils.LrcParser

/**
 * Lyrics for a track, as returned by the server's `metadata/get_track_lyrics`.
 *
 * [Synced] carries timestamped LRC lines (highlight/auto-scroll with playback);
 * [Plain] is an unsynced text block. The absence of lyrics is modelled as a
 * `null` result at the repository boundary rather than a variant here.
 */
sealed interface Lyrics {
    data class Synced(val lines: List<LrcLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

/** A single timestamped LRC line. [timeMs] is the offset from track start. */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * The lyrics a track carries, or null when it has none.
 *
 * [Lyrics.Synced] wins over the plain text when the LRC payload parses to at least one line.
 */
val Track.lyrics: Lyrics?
    get() = metadata?.let { metadata ->
        val plain = metadata.lyrics
        val lrc = metadata.lrcLyrics
        when {
            !lrc.isNullOrBlank() -> LrcParser.parse(lrc).takeIf { it.isNotEmpty() }
                ?.let { Lyrics.Synced(it) }
                ?: Lyrics.Plain(lrc)

            !plain.isNullOrBlank() -> Lyrics.Plain(plain)
            else -> null
        }
    }
