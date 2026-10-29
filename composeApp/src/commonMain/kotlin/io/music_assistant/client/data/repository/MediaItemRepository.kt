package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.api.fetchAllPages
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.lyricsOf
import io.music_assistant.client.data.model.client.transcriptOf
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerTranscriptCue
import io.music_assistant.client.data.model.server.events.FavoriteUpdatedEvent
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.data.model.server.events.ServerFavoriteUpdate
import io.music_assistant.client.data.model.server.pagesPlaylistTracksAndEpisodes
import io.music_assistant.client.data.model.server.sendsFavoriteUpdates
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration.Companion.seconds

interface MediaItemRepository {
    suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>>

    /**
     * Every item of a `playlist_tracks` or `podcast_episodes` [request]. Null when nothing could
     * be loaded; a page failure after the first page gives the items loaded so far.
     */
    suspend fun fetchAllMediaItems(request: Request): List<AppMediaItem>?
    val itemChanges: SharedFlow<MediaItemChange>
    suspend fun search(request: Request): Result<SearchResultData>
    suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?>

    /** The lyrics the server finds for [track]; `Result.success(null)` when it has none. */
    suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?>
    fun supportsRadioTracks(): Boolean

    /** The transcript of [episode] as [Lyrics]; `Result.success(null)` when the server has none. */
    suspend fun fetchEpisodeTranscript(episode: PodcastEpisode): Result<Lyrics?>

    fun publishLocalChange(change: MediaItemChange)
}

/**
 * Single seam between RPC + DTO land and the UI's typed `AppMediaItem` world.
 *
 * Wraps [ServiceClient.sendRequest] + [MediaItemFactory] so ViewModels stop
 * seeing [ServerMediaItem], [SearchResult], or `MediaItem*Event` directly,
 * and centralizes the "decode payload + map to client model" boilerplate
 * that was duplicated across every list/get/search call site.
 */

class ServiceClientMediaItemRepository(
    private val apiClient: ServiceClient,
    private val factory: MediaItemFactory,
    // The answer resumes on the caller's dispatcher, usually Main; decode and map off it.
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MediaItemRepository {
    // Singleton-scoped app-lifetime job; only used to keep [itemChanges]
    // hot. Survives subscriber turnover so quick navigation between screens
    // doesn't churn the upstream `apiClient.events` subscription.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Issue [request] and decode its payload as a list of client media items.
     * Failures (RPC error, decode failure, missing payload) surface as a
     * failed [Result] so callers can still log via `result.exceptionOrNull()`.
     */
    override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> =
        withContext(workDispatcher) {
            apiClient.sendRequest(request).mapCatching { answer ->
                answer.resultAs<List<ServerMediaItem>>()
                    ?.let(factory::createList)
                    ?: error("Missing or undecodable media list payload")
            }
        }

    /**
     * A server from 2.11 answers one page, so the pages are walked. An older server streams the
     * whole list on the plain request (see [pagesPlaylistTracksAndEpisodes]).
     */
    override suspend fun fetchAllMediaItems(request: Request): List<AppMediaItem>? =
        if (pagesPlaylistTracksAndEpisodes(schemaVersion())) {
            request.fetchAllPages { fetchMediaItems(it).getOrNull() }
        } else {
            fetchMediaItems(request).getOrNull()
        }

    private fun schemaVersion(): Int? =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion

    override fun supportsRadioTracks(): Boolean =
        schemaVersion()?.let { it >= SERVER_2_11_SCHEMA } == true

    /**
     * Issue [request] and decode its payload as a single client media item.
     * Returns `Result.success(null)` for an absent payload so a 404-style
     * "not found" stays distinguishable from a transport failure.
     */
    override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> =
        withContext(workDispatcher) {
            apiClient.sendRequest(request).map { answer ->
                answer.resultAs<ServerMediaItem>()?.let(factory::create)
            }
        }

    /** Issue [request] and decode its payload as a typed [SearchResultData]. */
    override suspend fun search(request: Request): Result<SearchResultData> =
        withContext(workDispatcher) {
            apiClient.sendRequest(request).mapCatching { answer ->
                answer.resultAs<SearchResult>()
                    ?.let(factory::createSearchResult)
                    ?: error("Missing or undecodable search payload")
            }
        }

    /** `metadata/get_track_lyrics` answers a `[lyrics, lrc_lyrics]` tuple. */
    override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> =
        withContext(workDispatcher) {
            apiClient.sendRequest(Request.Metadata.getTrackLyrics(track)).map { answer ->
                answer.resultAs<List<String?>>()
                    ?.let { lyricsOf(plain = it.getOrNull(0), lrc = it.getOrNull(1)) }
            }
        }

    /** `music/podcasts/podcast_episode_transcript` answers a `[text, cues]` tuple. */
    override suspend fun fetchEpisodeTranscript(episode: PodcastEpisode): Result<Lyrics?> =
        withContext(workDispatcher) {
            val request = Request.Podcast.getEpisodeTranscript(episode.itemId, episode.provider)
            apiClient.sendRequest(request).mapCatching { answer ->
                answer.resultAs<JsonArray>()?.let { tuple ->
                    transcriptOf(
                        text = tuple.getOrNull(0)?.let { myJson.decodeFromJsonElement<String?>(it) },
                        cues = tuple.getOrNull(1)?.let { myJson.decodeFromJsonElement<List<ServerTranscriptCue>?>(it) },
                    )
                }
            }
        }

    // Client-originated changes for mutations the server doesn't echo back as an
    // event (e.g. mark played/unplayed only writes the playlog server-side). Merged
    // into [itemChanges] so the same subscribers reconcile them like server events.
    private val localChanges = MutableSharedFlow<MediaItemChange>(extraBufferCapacity = 16)

    /** Publish an optimistic, client-originated [change] to [itemChanges] subscribers. */
    override fun publishLocalChange(change: MediaItemChange) {
        localChanges.tryEmit(change)
    }

    /**
     * Hot stream of library lifecycle changes mapped from the corresponding
     * server events (plus client-originated [localChanges]). ViewModels collect
     * this instead of filtering `apiClient.events` and re-running
     * `mediaItemFactory.create(...)` themselves. Replay is zero — late
     * subscribers only see future changes.
     */
    override val itemChanges: SharedFlow<MediaItemChange> = merge(
        localChanges,
        apiClient.events
            // The socket reader suspends on a full event buffer, and the favorite fetch below
            // waits for that reader. Buffer here so the fetch never blocks the reader.
            .buffer(Channel.UNLIMITED)
            .mapNotNull { event ->
                val carriesFavorite = !sendsFavoriteUpdates(schemaVersion())
                when (event) {
                    is MediaItemAddedEvent ->
                        factory.create(event.data)
                            ?.let { MediaItemChange.Added(it, carriesFavorite) }

                    is MediaItemUpdatedEvent ->
                        factory.create(event.data)
                            ?.let { MediaItemChange.Updated(it, carriesFavorite) }

                    is MediaItemDeletedEvent ->
                        factory.create(event.data.withLibraryStripped())
                            ?.let(MediaItemChange::Deleted)

                    is FavoriteUpdatedEvent -> favoriteUpdated(event.data)

                    else -> null
                }
            },
    ).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

    // The event has no item body, so fetch the item to match rows by provider mappings. The
    // state of the event wins: events come in order, and a fetch can answer after a newer change.
    // The fetch holds every later change, so it has a time limit.
    private suspend fun favoriteUpdated(update: ServerFavoriteUpdate): MediaItemChange? =
        withTimeoutOrNull(FAVORITE_FETCH_TIMEOUT) {
            fetchMediaItem(Request.Library.itemByUri(update.uri)).getOrNull()
        }?.let { MediaItemChange.FavoriteUpdated(it.withFavorite(update.favorite)) }
            ?: run {
                Logger.withTag("MediaItemRepository").w { "No item for favorite update of ${update.uri}" }
                null
            }

    // When the server announces the deletion of a library record, the
    // *underlying* provider item still exists. Re-key the DTO to its first
    // non-library provider mapping so subscribers can fall through to the
    // source identity instead of dangling on a library id that's about to
    // 404. No-op when there's no fallback mapping.
    private fun ServerMediaItem.withLibraryStripped(): ServerMediaItem {
        val fallback = providerMappings?.firstOrNull() ?: return this
        return copy(
            itemId = fallback.itemId,
            provider = fallback.providerInstance,
            favorite = null,
            uri = "${fallback.providerInstance}://$mediaType/${fallback.itemId}",
        )
    }

    private companion object {
        val FAVORITE_FETCH_TIMEOUT = 10.seconds
    }
}

/**
 * The home-page recommendation rows as the server returned them
 */
suspend fun MediaItemRepository.fetchRecommendationRows(): Result<List<RecommendationFolder>> =
    fetchMediaItems(Request.Library.recommendations())
        .map { items -> items.filterIsInstance<RecommendationFolder>() }

/** Items of one recommendation row, or null when the fetch failed (logged). */
suspend fun MediaItemRepository.fetchRecommendationRowItems(
    folder: RecommendationFolder,
): List<AppMediaItem>? = fetchMediaItems(
    Request.Library.recommendationItems(folder.provider, folder.itemId),
).getOrElse { error ->
    if (error is CancellationException) throw error
    Logger.w(
        "Failed fetching recommendation items for " +
                "${folder.provider}/${folder.itemId}: $error",
    )
    null
}

/**
 * One-shot, fully-resolved recommendation rows for consumers without a
 * progressive UI (e.g. CarPlay lists).
 */
suspend fun MediaItemRepository.fetchRecommendationFolders(): Result<List<RecommendationFolder>> {
    val folders = fetchRecommendationRows().getOrElse { error ->
        if (error is CancellationException) throw error
        return Result.failure(error)
    }

    return Result.success(
        coroutineScope {
            folders.map { folder ->
                async {
                    folder.copy(items = fetchRecommendationRowItems(folder).orEmpty())
                }
            }.awaitAll()
        },
    )
}

/**
 * Fetch items for multiple requests and combine them into one list. Returns a failure Result
 * if any of the requests fail (and fails fast).
 */
 suspend fun MediaItemRepository.fetchMediaItems(requests: List<Request>): Result<List<AppMediaItem>> {
     val items = mutableListOf<AppMediaItem>()
     for (request in requests) {
         val result = this.fetchMediaItems(request)
         if (result.isSuccess) {
             items += result.getOrEmptyList()
         } else {
             return result
         }
     }

     return Result.success(items)
 }
