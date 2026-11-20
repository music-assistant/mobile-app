// debounce() is FlowPreview; the debounce window is documented at use site.
package io.music_assistant.client.services

import android.annotation.SuppressLint
import android.content.Context
import android.provider.BaseColumns
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.isTelevision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Single slot: the row represents "resume the app to what it was last showing," not a
// per-player history, so every update targets the same internal provider id.
private const val WATCH_NEXT_INTERNAL_ID = "now_playing"
private const val WATCH_NEXT_DEBOUNCE_MS = 500L

/** What the Watch Next row should show. A null [watchNextContentFor] result means no row at all. */
internal data class WatchNextContent(
    val title: String,
    val subtitle: String?,
    val posterArtUrl: String?,
    val deepLinkUri: String,
    val durationMillis: Int?,
    val lastPlaybackPositionMillis: Int?,
)

/** Nothing to resume without a current track -- mirrors the notification's own content gate. */
internal fun watchNextContentFor(player: PlayerData?): WatchNextContent? {
    val media = player?.player?.currentMedia ?: return null
    val title = media.title ?: return null
    return WatchNextContent(
        title = title,
        subtitle = media.subtitle,
        posterArtUrl = media.imageUrl,
        deepLinkUri = "musicassistant://app/players?player=${player.playerId}",
        durationMillis = media.duration?.let { (it * 1000).toInt() },
        lastPlaybackPositionMillis = player.queueInfo?.elapsedTime?.let { (it * 1000).toInt() },
    )
}

/**
 * Publishes a "Continue listening" row to Android TV's home-screen Watch Next shelf,
 * mirroring whatever [MainDataSource.nowPlayingPlayer] is currently showing.
 *
 * Android TV has no notification shade, and nothing else surfaces a way back into the
 * app once its activity is gone (TV screensaver, task switch, app list) -- this is that
 * surface's equivalent on the home screen. TV-only: a phone/tablet install has no Watch
 * Next shelf to publish to, and [start] no-ops there.
 *
 * A stale entry left behind on pause/disconnect is worse than none (a dead end on tap),
 * so every content change clears the row first and this manager owns no other state:
 * the row itself, keyed by [WATCH_NEXT_INTERNAL_ID], is the only record of "do we have
 * one published already."
 */
@OptIn(FlowPreview::class)
class WatchNextManager(
    private val context: Context,
    private val dataSource: MainDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val logger = Logger.withTag("WatchNextManager")

    fun start() {
        if (!context.resources.configuration.isTelevision()) return
        scope.launch {
            dataSource.nowPlayingPlayer
                .map { watchNextContentFor(it) }
                .distinctUntilChanged()
                .debounce(WATCH_NEXT_DEBOUNCE_MS)
                .collect { content ->
                    if (content != null) upsert(content) else remove()
                }
        }
    }

    // WatchNextProgram.Builder's setters are this library's only public way to build a row,
    // but lint still flags them as @RestrictTo even on 1.1.0 (the latest and only other
    // stable release besides 1.0.0, confirmed against Google's own maven-metadata.xml) --
    // a packaging leftover on Google's side, not a sign this is the wrong call site.
    @SuppressLint("RestrictedApi")
    private suspend fun upsert(content: WatchNextContent) = withContext(Dispatchers.IO) {
        runCatching {
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_TRACK)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setInternalProviderId(WATCH_NEXT_INTERNAL_ID)
                .setTitle(content.title)
                .apply { content.subtitle?.let(::setDescription) }
                .apply { content.posterArtUrl?.let { setPosterArtUri(it.toUri()) } }
                .apply { content.durationMillis?.let(::setDurationMillis) }
                .apply { content.lastPlaybackPositionMillis?.let(::setLastPlaybackPositionMillis) }
                .setIntentUri(content.deepLinkUri.toUri())
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .build()

            val existingId = findRowId()
            if (existingId != null) {
                context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingId),
                    program.toContentValues(),
                    null,
                    null,
                )
            } else {
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues(),
                )
            }
        }.onFailure { logger.w(it) { "Could not publish Watch Next row" } }
    }

    private suspend fun remove() = withContext(Dispatchers.IO) {
        runCatching {
            val existingId = findRowId() ?: return@runCatching
            context.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(existingId),
                null,
                null,
            )
        }.onFailure { logger.w(it) { "Could not clear Watch Next row" } }
    }

    // No selection clause: the real TvProvider on at least one Google TV device rejects any
    // selection on this URI outright (SecurityException: "Selection not allowed"), verified
    // live. The provider only ever returns this app's own rows regardless, and there is at
    // most one, so scanning the unfiltered result for our id client-side is cheap and safe.
    private fun findRowId(): Long? =
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(BaseColumns._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val providerIdIndex =
                cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .firstOrNull { it.getString(providerIdIndex) == WATCH_NEXT_INTERNAL_ID }
                ?.getLong(idIndex)
        }
}
