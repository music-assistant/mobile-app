package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.data.model.client.items.isLongFormSpokenContent
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
)

/**
 * A transport anchor. [anchorMs] belongs only to the Kotlin monotonic clock;
 * it must never be compared with a timestamp produced across the language
 * bridge. Swift re-anchors the elapsed value with its own clock on delivery.
 * [rate] is zero while paused or seek-frozen; otherwise it carries the queue's
 * playback speed so variable-rate spoken content remains synchronized.
 */
data class NowPlayingTransport(
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
     * The caller supplies content identity because a new track always needs a
     * fresh anchor, even when its position happens to equal the old track's.
     */
    fun sameTransport(
        oldMediaItemId: String?,
        old: NowPlayingTransport?,
        newMediaItemId: String?,
        new: NowPlayingTransport?,
    ): Boolean {
        if (oldMediaItemId != newMediaItemId) return false
        if (old == null || new == null) return old == new
        if (old.isPlaying != new.isPlaying || old.rate != new.rate) return false

        if (old.elapsedSec == null || new.elapsedSec == null) {
            return old.elapsedSec == new.elapsedSec
        }

        val projectedOld = old.elapsedSec +
            (new.anchorMs - old.anchorMs) / 1000.0 * old.rate
        return abs(projectedOld - new.elapsedSec) < ELAPSED_ANCHOR_EPSILON_S
    }
}

/** Maps local-player state to the metadata channel. */
internal fun buildNowPlayingTrack(playerData: PlayerData?): NowPlayingTrack? =
    playerData?.queueInfo?.currentItem?.track?.toNowPlayingTrack()

/**
 * Maps local-player state to an anchor, or null when there is no current item.
 * Tracker interpolation and the published rate use the same queue speed.
 */
internal fun buildNowPlayingTransport(
    playerData: PlayerData?,
    positionTracker: PlayerPositionTracker,
    anchorMs: Long = monotonicMs(),
): NowPlayingTransport? {
    val queueInfo = playerData?.queueInfo ?: return null
    queueInfo.currentItem?.track ?: return null
    val isPlaying = playerData.player.isPlaying
    return NowPlayingTransport(
        isPlaying = isPlaying,
        elapsedSec = positionTracker.effectiveSec(queueInfo.id) ?: queueInfo.elapsedTime,
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

/** Internal key used to reset transport comparison at a content transition. */
internal data class NowPlayingTransportEmission(
    val mediaItemId: String?,
    val transport: NowPlayingTransport?,
)
