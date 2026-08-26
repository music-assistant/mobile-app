package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import kotlin.math.ceil

/**
 * Chapter resolved at a playback position with an effective finite interval.
 * When [end] is absent, use the next chapter start, media duration, or infinity.
 */
data class ResolvedChapter(
    val chapter: Chapter,
    val start: Double,
    val end: Double,
) {
    val duration: Double get() = end - start

    /** Chapter name for display surfaces; null when metadata ships a blank name. */
    val displayName: String? get() = chapter.name.takeIf { it.isNotBlank() }

    /** Chapter-relative view of an absolute position, clamped to the chapter. */
    fun relativeSec(absoluteSec: Double): Double =
        (absoluteSec - start).coerceIn(0.0, duration)

    /** Absolute position for a chapter-relative one, clamped to the chapter. */
    fun absoluteSec(relativeSec: Double): Double =
        start + relativeSec.coerceIn(0.0, duration)
}

/**
 * Chapters eligible for chapter-relative presentation; audiobooks only.
 */
fun PlayableItem?.progressChapters(): List<Chapter>? =
    (this as? Audiobook)?.chapters?.takeIf { it.isNotEmpty() }

/** Chapters eligible for previous/next navigation: audiobooks and podcasts. */
fun PlayableItem?.navigationChapters(): List<Chapter>? = when (this) {
    is Audiobook -> chapters
    is PodcastEpisode -> metadata?.chapters
    else -> null
}?.takeIf { it.isNotEmpty() }

/**
 * Resolves the finite audiobook chapter containing [elapsedSec], or null.
 * Callers apply the `audiobook_chapter_progress` preference gate.
 */
fun PlayerData.presentationChapter(elapsedSec: Double?): ResolvedChapter? =
    resolveCurrentChapter(
        chapters = queueInfo?.currentItem?.track.progressChapters(),
        elapsedSec = elapsedSec,
        mediaDurationSec = player.currentMedia?.duration
            ?: queueInfo?.currentItem?.track?.duration,
    )?.takeIf { it.duration.isFinite() }

/**
 * Whole-second seek target for a fractional chapter position. The seek API
 * takes integer seconds, so round up — a truncated target lands a fraction of
 * a second before the chapter start, inside the prior chapter.
 */
fun chapterSeekSeconds(positionSec: Double): Long = ceil(positionSec).toLong()

/**
 * Absolute whole-second seek target for a value read off a presentation timeline.
 * With a chapter the timeline is chapter-relative, so map it back and round up;
 * without one the timeline is already absolute and keeps the truncating target
 * the server and PlayerPositionTracker agree on. Every host surface that owns
 * a scrubber converts through here, so the two coordinate systems meet in one place.
 */
fun ResolvedChapter?.toAbsoluteSeekSeconds(timelineSec: Double): Long =
    this?.let { chapterSeekSeconds(it.absoluteSec(timelineSec)) } ?: timelineSec.toLong()

/**
 * Wall-clock ms until [chapter]'s end at queue speed, or null if no wake-up is needed.
 * Adds a pad so boundary re-resolution lands inside the next chapter.
 *
 * The end can already be behind the position: [resolveCurrentChapter] holds the
 * final chapter at exact media completion. There is no boundary left to wake for,
 * so return null instead of a pad-length delay that would re-resolve at 4 Hz.
 */
fun PlayerData.msUntilChapterEnd(chapter: ResolvedChapter?, elapsedSec: Double?): Long? {
    if (chapter == null || elapsedSec == null || !player.isPlaying) return null
    val speed = (queueInfo?.playbackSpeed ?: 1.0).takeIf { it > 0 } ?: return null
    val mediaSecondsLeft = (chapter.end - elapsedSec).takeIf { it > 0 } ?: return null
    return ((mediaSecondsLeft / speed) * 1000).toLong() + CHAPTER_BOUNDARY_PAD_MS
}

private const val CHAPTER_BOUNDARY_PAD_MS = 250L

/**
 * Resolves an absolute position using half-open intervals; the final chapter
 * remains active at exact media completion. Metadata is sorted by start.
 */
fun resolveCurrentChapter(
    chapters: List<Chapter>?,
    elapsedSec: Double?,
    mediaDurationSec: Double?,
): ResolvedChapter? {
    if (elapsedSec == null || !elapsedSec.isFinite() || chapters.isNullOrEmpty()) return null

    val ordered = chapters.filter { it.start.isFinite() }.sortedBy { it.start }
    ordered.forEachIndexed { index, chapter ->
        val nextStart = ordered.getOrNull(index + 1)?.start
        val end = chapter.end ?: nextStart ?: mediaDurationSec ?: Double.POSITIVE_INFINITY
        if (end <= chapter.start) return@forEachIndexed
        val isFinalMediaPosition = mediaDurationSec != null && mediaDurationSec.isFinite() &&
            elapsedSec == mediaDurationSec && end == mediaDurationSec
        if (elapsedSec >= chapter.start && (elapsedSec < end || isFinalMediaPosition)) {
            return ResolvedChapter(chapter = chapter, start = chapter.start, end = end)
        }
    }
    return null
}
