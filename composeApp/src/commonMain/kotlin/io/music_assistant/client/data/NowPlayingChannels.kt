package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.ResolvedChapter
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.data.model.client.items.isLongFormSpokenContent
import io.music_assistant.client.data.model.client.navigationChapters
import io.music_assistant.client.utils.monotonicMs
import kotlin.math.abs

/**
 * Metadata that changes with the content identity rather than with transport.
 *
 * [mediaItemId] identifies the content, not its queue entry. Consequently, the
 * same song queued twice produces the same value and does not re-render system
 * metadata unless one of its displayed fields changed.
 */
data class NowPlayingTrack(
    val mediaItemId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val duration: Double?,
    val isLongFormContent: Boolean,
    // Pref-gated: remote next/previous will chapter-jump for this content.
    val hasChapterNavigation: Boolean = false,
)

/**
 * A transport anchor. [anchorMs] belongs only to the Kotlin monotonic clock;
 * it must never be compared with a timestamp produced across the language
 * bridge. Swift re-anchors the elapsed value with its own clock on delivery.
 * [rate] is zero while paused or seek-frozen; otherwise it carries the queue's
 * playback speed so variable-rate spoken content remains synchronized.
 *
 * [mediaItemId] correlates the anchor with the track it belongs to. The track
 * and transport channels are delivered independently with no cross-channel
 * ordering guarantee, so the consumer must be able to reject (or hold) an
 * anchor that does not match the track it is currently presenting. It is a
 * correlation stamp only — never rendered from this channel.
 */
data class NowPlayingTransport(
    val mediaItemId: String,
    val isPlaying: Boolean,
    val elapsedSec: Double?,
    val anchorMs: Long,
    val rate: Double,
)

/** Current queue modes and whether their system controls are available. */
data class NowPlayingModes(
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
    val togglesEnabled: Boolean,
)

/** Change detection shared by the channel flow and its plain-value tests. */
internal object NowPlayingChannelChangeDetection {
    /** Position drift below this threshold is already covered by iOS interpolation. */
    const val ELAPSED_ANCHOR_EPSILON_S = 2.0

    /**
     * Returns whether two transport values can share one system-media anchor.
     * A new track always needs a fresh anchor, even when its position happens
     * to equal the old track's, so identity is compared first.
     */
    fun sameTransport(
        old: NowPlayingTransport?,
        new: NowPlayingTransport?,
    ): Boolean {
        if (old == null || new == null) return old == new
        if (old.mediaItemId != new.mediaItemId) return false
        if (old.isPlaying != new.isPlaying || old.rate != new.rate) return false

        if (old.elapsedSec == null || new.elapsedSec == null) {
            return old.elapsedSec == new.elapsedSec
        }

        val projectedOld = old.elapsedSec +
            (new.anchorMs - old.anchorMs) / 1000.0 * old.rate
        return abs(projectedOld - new.elapsedSec) < ELAPSED_ANCHOR_EPSILON_S
    }
}

/**
 * [currentChapter] switches duration/album to the chapter presentation.
 * [NowPlayingTrack.hasChapterNavigation] is only true when
 * [chapterNavigationEnabled] (the `audiobook_chapter_progress` preference) is set.
 */
internal fun buildNowPlayingTrack(
    playerData: PlayerData?,
    currentChapter: ResolvedChapter? = null,
    chapterNavigationEnabled: Boolean = false,
): NowPlayingTrack? {
    val currentItem = playerData?.queueInfo?.currentItem ?: return null
    val base = withRadioStreamMetadata(
        base = currentItem.track.toNowPlayingTrack(),
        playerData = playerData,
        currentItem = currentItem,
    ).copy(
        hasChapterNavigation = chapterNavigationEnabled &&
            currentItem.track.navigationChapters() != null,
    )
    if (currentChapter == null) return base
    return base.copy(
        album = currentChapter.displayName ?: base.album,
        duration = currentChapter.duration,
    )
}

/**
 * Overlay the radio stream's dynamic metadata (from `currentMedia`) onto the static
 * station entry. [NowPlayingTrack.mediaItemId] stays the station's so transport
 * anchors correlate and Swift treats stream-title changes as same-track updates.
 */
private fun withRadioStreamMetadata(
    base: NowPlayingTrack,
    playerData: PlayerData,
    currentItem: QueueTrack,
): NowPlayingTrack {
    if (currentItem.track.mediaType != MediaType.RADIO) return base
    val media = playerData.player.currentMedia ?: return base
    // The server always stamps queue_item_id when an MA queue item is current;
    // media stamped otherwise is not this station's stream.
    if (media.queueItemId != currentItem.id) return base
    // A title equal to the station name carries no information (idle streams and
    // the synthesized pre-play fallback both produce it).
    val streamTitle = media.title?.takeIf { it.isNotBlank() && it != base.title } ?: return base
    return base.copy(
        title = streamTitle,
        artist = media.artist?.takeIf { it.isNotBlank() },
        album = base.title,
        artworkUrl = media.imageUrl ?: base.artworkUrl,
    )
}

/**
 * Maps local state to a transport anchor; [currentChapter] makes elapsed time
 * chapter-relative while tracker and seek coordinates remain absolute.
 */
internal fun buildNowPlayingTransport(
    playerData: PlayerData?,
    positionTracker: PlayerPositionTracker,
    anchorMs: Long = monotonicMs(),
    currentChapter: ResolvedChapter? = null,
): NowPlayingTransport? {
    val queueInfo = playerData?.queueInfo ?: return null
    val track = queueInfo.currentItem?.track ?: return null
    val isPlaying = playerData.player.isPlaying
    val absoluteElapsedSec = positionTracker.effectiveSec(queueInfo.id) ?: queueInfo.elapsedTime
    return NowPlayingTransport(
        mediaItemId = track.itemId,
        isPlaying = isPlaying,
        elapsedSec = absoluteElapsedSec?.let { currentChapter?.relativeSec(it) ?: it },
        anchorMs = anchorMs,
        rate = if (isPlaying && !positionTracker.isFrozenUntilConfirmed(queueInfo.id)) {
            queueInfo.playbackSpeed ?: 1.0
        } else {
            0.0
        },
    )
}

/** Maps queue modes and the shared availability gates to the modes channel. */
internal fun buildNowPlayingModes(playerData: PlayerData?): NowPlayingModes? {
    val queueInfo = playerData?.queueInfo ?: return null
    val track = queueInfo.currentItem?.track ?: return null
    return NowPlayingModes(
        shuffleEnabled = queueInfo.shuffleEnabled,
        repeatMode = queueInfo.repeatMode,
        togglesEnabled = nowPlayingTogglesEnabled(
            isDynamicPlaylist = queueInfo.isDynamicPlaylist,
            isLongFormContent = track.isLongFormSpokenContent,
        ),
    )
}

/** The same availability gates used by the in-app controls and Android media UI. */
internal fun nowPlayingTogglesEnabled(
    isDynamicPlaylist: Boolean,
    isLongFormContent: Boolean,
): Boolean = !isDynamicPlaylist && !isLongFormContent

private fun PlayableItem.toNowPlayingTrack(): NowPlayingTrack = NowPlayingTrack(
    mediaItemId = itemId,
    title = displayName,
    artist = subtitle,
    album = parentName,
    artworkUrl = image(ImageType.THUMB)?.url,
    duration = duration,
    isLongFormContent = isLongFormSpokenContent,
)
