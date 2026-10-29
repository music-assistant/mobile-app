package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.ServerTranscriptCue

private const val MS_PER_SECOND = 1000.0

/**
 * The transcript of a podcast episode as [Lyrics], so the lyrics sheet shows it with the same
 * timing. `music/podcasts/podcast_episode_transcript` answers a `[text, cues]` tuple: [Lyrics.Synced]
 * from the timed [cues] when there are any, else [Lyrics.Plain] from the readable [text], else null.
 */
fun transcriptOf(text: String?, cues: List<ServerTranscriptCue>?): Lyrics? = when {
    !cues.isNullOrEmpty() -> Lyrics.Synced(
        cues.filter { it.text.isNotBlank() }
            .sortedBy { it.start }
            .map { LrcLine(timeMs = (it.start * MS_PER_SECOND).toLong(), text = it.text.trim()) },
    )

    !text.isNullOrBlank() -> Lyrics.Plain(text)
    else -> null
}
