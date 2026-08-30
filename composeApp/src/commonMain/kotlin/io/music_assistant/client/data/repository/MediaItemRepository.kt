package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

interface MediaItemRepository {
    suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>>
    val itemChanges: SharedFlow<MediaItemChange>
    suspend fun search(request: Request): Result<SearchResultData>
    suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?>
    fun supportsRecommendationRowItems(): Boolean
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
        apiClient.sendRequest(request).mapCatching { answer ->
            answer.resultAs<List<ServerMediaItem>>()
                ?.let(factory::createList)
                ?: error("Missing or undecodable media list payload")
        }

    /**
     * Whether the connected server strips the items from `music/recommendations`
     * rows and serves each row's contents via `music/recommendations/items`
     * instead. This can be simplified once v2.10 is our minimum supported server
     * version.
     */
    override fun supportsRecommendationRowItems(): Boolean =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
            ?.let { it >= RECOMMENDATION_ITEMS_SCHEMA } == true

    /**
     * Issue [request] and decode its payload as a single client media item.
     * Returns `Result.success(null)` for an absent payload so a 404-style
     * "not found" stays distinguishable from a transport failure.
     */
    override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> =
        apiClient.sendRequest(request).map { answer ->
            answer.resultAs<ServerMediaItem>()?.let(factory::create)
        }

    /** Issue [request] and decode its payload as a typed [SearchResultData]. */
    override suspend fun search(request: Request): Result<SearchResultData> =
        apiClient.sendRequest(request).mapCatching { answer ->
            answer.resultAs<SearchResult>()
                ?.let(factory::createSearchResult)
                ?: error("Missing or undecodable search payload")
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
        apiClient.events.mapNotNull { event ->
            when (event) {
                is MediaItemAddedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Added)

                is MediaItemUpdatedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Updated)

                is MediaItemDeletedEvent ->
                    factory.create(event.data.withLibraryStripped())
                        ?.let(MediaItemChange::Deleted)

                else -> null
            }
        },
    ).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

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
}

/**
 * The home-page recommendation rows as the server returned them, with or
 * without embedded items (see [supportsRecommendationRowItems]).
 */
suspend fun MediaItemRepository.fetchRecommendationRows(): Result<List<RecommendationFolder>> =
    withContext(Dispatchers.IO) {
        fetchMediaItems(Request.Library.recommendations())
            .map { items -> items.filterIsInstance<RecommendationFolder>() }
    }

/** Items of one recommendation row, or null when the fetch failed (logged). */
suspend fun MediaItemRepository.fetchRecommendationRowItems(
    folder: RecommendationFolder,
): List<AppMediaItem>? = withContext(Dispatchers.IO) {
    fetchMediaItems(
        Request.Library.recommendationItems(folder.provider, folder.itemId),
    ).getOrElse { error ->
        if (error is CancellationException) throw error
        Logger.w(
            "Failed fetching recommendation items for " +
                    "${folder.provider}/${folder.itemId}: $error",
        )
        null
    }
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
    if (!supportsRecommendationRowItems()) return Result.success(folders)

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
 * Keeps a [Flow] of [DataState] holding a list of [AppMediaItem] up to date with changes from the
 * server so that changing favorites etc. will get updated immediately.
 *
 */
fun Flow<DataState<List<AppMediaItem>>>.updateFrom(mediaItemRepository: MediaItemRepository): Flow<DataState<List<AppMediaItem>>> {
    /**
     * Create a nullable default version of [MediaItemRepository.itemChanges] so that the result of
     * `combine` doesn't wait for a change to emit. There isn't currently a [Flow] transformation
     * that behaves like this: `zip` emits new pairs (it "buffers" one side until the other emits
     * a corresponding value) and `combine` only emits once both [Flow] instances have emitted.
     */
    val nullableItemChanges: Flow<MediaItemChange?> = mediaItemRepository.itemChanges.map { it }
    val itemChangesWithDefault = nullableItemChanges.onStart { emit(null) }

    return this.combine(itemChangesWithDefault) { data, change ->
        if (change != null) {
            when (data) {
                is DataState.Data -> data.copy(data = data.data.replacing(change.item))
                is DataState.Stale -> data.copy(data = data.data.replacing(change.item))
                else -> data
            }
        } else {
            data
        }
    }
}

private fun <T : AppMediaItem> List<T>.replacing(changed: T): List<T> =
    map { if (it.itemId == changed.itemId) changed else it }

/** Server schema version that split `music/recommendations` into rows + per-row items. */
private const val RECOMMENDATION_ITEMS_SCHEMA = 39
