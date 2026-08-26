package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.ResolvedChapter
import io.music_assistant.client.data.model.client.msUntilChapterEnd
import io.music_assistant.client.data.model.client.presentationChapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest

/**
 * A player state paired with the chapter its host surface must present, plus the
 * live position both were resolved from — so a consumer never re-reads a position
 * that has moved since.
 */
data class ChapterPresentation<T>(
    val value: T,
    val chapter: ResolvedChapter?,
    val elapsedSec: Double?,
)

/**
 * Pairs every upstream state with its presentation chapter and re-emits at chapter
 * boundaries. No server event announces a presentation change, so the boundary
 * wake-up is the only trigger for it; when no wake-up is due the loop waits for the
 * next upstream emission instead.
 *
 * [playerOf] pulls the player out of the upstream element, so surfaces that carry
 * extra state alongside it share this one timer rather than each growing a copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.withPresentationChapter(
    preferences: UserPreferences,
    positionTracker: PlayerPositionTracker,
    playerOf: (T) -> PlayerData?,
): Flow<ChapterPresentation<T>> =
    combine(this, preferences.chapterProgressEnabled, ::Pair)
        .transformLatest { (value, chapterEnabled) ->
            val playerData = playerOf(value)
            while (true) {
                val elapsedSec = playerData?.queueInfo?.id?.let(positionTracker::effectiveSec)
                val chapter = playerData?.presentationChapter(elapsedSec)
                    ?.takeIf { chapterEnabled }
                emit(ChapterPresentation(value, chapter, elapsedSec))
                delay(playerData?.msUntilChapterEnd(chapter, elapsedSec) ?: break)
            }
        }
