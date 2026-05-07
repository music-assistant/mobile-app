package io.music_assistant.client.auto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import co.touchlab.kermit.Logger
import io.music_assistant.client.R
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(FlowPreview::class)
class AutoLibrary(
    private val context: Context,
    private val apiClient: ServiceClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val searchFlow: MutableStateFlow<Pair<String, MediaBrowserServiceCompat.Result<List<MediaItem>>>?> =
        MutableStateFlow(null)
    private val defaultIconUri = R.drawable.baseline_library_music_24.toUri(context)

    init {
        scope.launch {
            searchFlow
                .filterNotNull()
                .filter { it.first.isNotEmpty() }
                .debounce(Timings.INPUT_DEBOUNCE)
                .collect { (query, result) ->
                    val answer = apiClient.sendRequest(
                        request = Request.Library.search(
                            query = query,
                            mediaTypes = listOf(
                                MediaType.ARTIST,
                                MediaType.ALBUM,
                                MediaType.TRACK,
                                MediaType.PLAYLIST,
                                MediaType.AUDIOBOOK,
                                MediaType.PODCAST,
                                MediaType.RADIO,
                            ),
                            libraryOnly = false,
                        ),
                    )
                    answer.resultAs<SearchResult>()?.let {
                        result.sendResult(
                            it.toAutoMediaItems(
                                baseUrl,
                                defaultIconUri,
                            ),
                        )
                    } ?: result.sendResult(null)
                }
        }
    }

    fun getItems(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        Logger.withTag("AutoLibrary").i { "Items for $id" }
        when {
            id == MediaIds.ROOT -> result.sendResult(rootChildren())
            MediaIds.tabMediaTypeOf(id) != null -> handleTabContent(
                id,
                result,
                favoritesOnly = false,
            )

            id.startsWith(MediaIds.FAVORITES_PREFIX) -> handleFavorites(id, result)
            else -> handleDrillDown(id, result)
        }
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        rootTabItem("Artists", MediaIds.TAB_ARTISTS),
        rootTabItem("Albums", MediaIds.TAB_ALBUMS),
        rootTabItem("Playlists", MediaIds.TAB_PLAYLISTS),
        rootTabItem("Podcasts", MediaIds.TAB_PODCASTS),
        rootTabItem("Radio", MediaIds.TAB_RADIO),
        rootTabItem("Audiobooks", MediaIds.TAB_AUDIOBOOKS),
    )

    private fun handleTabContent(
        tabId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
        favoritesOnly: Boolean,
    ) {
        val mediaType = MediaIds.tabMediaTypeOf(tabId) ?: run {
            result.sendResult(null)
            return
        }
        result.detach()
        scope.launch {
            if (!waitForCorrectState()) {
                result.sendResult(null)
                return@launch
            }
            val items = loadTabItems(mediaType, defaultSortFor(mediaType), favoritesOnly) ?: run {
                result.sendResult(null)
                return@launch
            }
            val header = if (favoritesOnly) {
                emptyList()
            } else {
                listOf(favoritesPseudoItem(MediaIds.favoritesId(mediaType)))
            }
            result.sendResult(header + items)
        }
    }

    private fun handleFavorites(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val mediaType = MediaIds.favoritesMediaTypeOf(id) ?: run {
            result.sendResult(null)
            return
        }
        // Reuse the tab content path with favoritesOnly=true; header is suppressed there.
        handleTabContent(MediaIds.tabIdOf(mediaType), result, favoritesOnly = true)
    }

    private fun handleDrillDown(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val parent = ParentRef.parse(id) ?: run {
            result.sendResult(null)
            return
        }
        val context = parent.subItemContext() ?: run {
            result.sendResult(null)
            return
        }
        result.detach()
        scope.launch {
            val items = loadSubItems(context, parent, defaultSortFor(context)) ?: run {
                result.sendResult(null)
                return@launch
            }
            result.sendResult(actionsForItem(id) + items)
        }
    }

    private suspend fun loadTabItems(
        mediaType: MediaType,
        sort: SortOption,
        favoritesOnly: Boolean,
    ): List<MediaItem>? {
        val orderBy = sort.toServerString()
        val favorite = if (favoritesOnly) true else null
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.listLibrary(orderBy = orderBy, favorite = favorite)
            MediaType.ALBUM -> Request.Album.listLibrary(orderBy = orderBy, favorite = favorite)
            MediaType.PLAYLIST -> Request.Playlist.listLibrary(
                orderBy = orderBy,
                favorite = favorite,
            )

            MediaType.PODCAST -> Request.Podcast.listLibrary(orderBy = orderBy, favorite = favorite)
            MediaType.RADIO -> Request.RadioStation.listLibrary(
                orderBy = orderBy,
                favorite = favorite,
            )

            MediaType.AUDIOBOOK -> Request.Audiobook.listLibrary(
                orderBy = orderBy,
                favorite = favorite,
            )

            else -> return null
        }
        return apiClient.sendRequest(request)
            .resultAs<List<ServerMediaItem>>()
            ?.toAppMediaItemList()
            ?.map { it.toAutoMediaItem(baseUrl, true, defaultIconUri) }
    }

    private suspend fun loadSubItems(
        context: SubItemContext,
        parent: ParentRef,
        sort: SortOption,
    ): List<MediaItem>? {
        // Server-side sort only works for playlist tracks; the rest must be sorted client-side.
        val request = when (context) {
            SubItemContext.ARTIST_ALBUMS ->
                Request.Artist.getAlbums(parent.itemId, parent.provider)

            SubItemContext.ALBUM_TRACKS ->
                Request.Album.getTracks(parent.itemId, parent.provider)

            SubItemContext.PLAYLIST_TRACKS ->
                Request.Playlist.getTracks(
                    itemId = parent.itemId,
                    providerInstanceIdOrDomain = parent.provider,
                    orderBy = sort.toServerString(),
                )

            SubItemContext.PODCAST_EPISODES ->
                Request.Podcast.getEpisodes(parent.itemId, parent.provider)

            SubItemContext.ARTIST_TRACKS -> return null
        }
        val items = apiClient.sendRequest(request)
            .resultAs<List<ServerMediaItem>>()
            ?.toAppMediaItemList()
            ?: return null
        val sorted =
            if (context == SubItemContext.PLAYLIST_TRACKS) items else items.clientSorted(sort)
        return sorted.map { it.toAutoMediaItem(baseUrl, true, defaultIconUri) }
    }

    // Hardcoded defaults — sorting UI is intentionally absent in AA. Per-list
    // pickers turned out to be a footgun: AA prefetches children of every
    // browsable preset, which (when coupled to persistence + parent invalidation)
    // looped the browse view to OOM. Keeping the matrix flat sidesteps the
    // entire surface.
    private fun defaultSortFor(mediaType: MediaType): SortOption =
        SortOption(SortField.NAME, descending = false)

    private fun defaultSortFor(context: SubItemContext): SortOption = when (context) {
        SubItemContext.PODCAST_EPISODES -> SortOption(SortField.RELEASE_DATE, descending = true)
        else -> SortOption(SortField.NAME, descending = false)
    }

    private suspend fun waitForCorrectState(): Boolean =
        withTimeoutOrNull(WAIT_FOR_AUTHENTICATED_TIMEOUT_MS) {
            apiClient.sessionState
                .mapNotNull { it as? SessionState.Connected }
                .mapNotNull { it.dataConnectionState as? DataConnectionState.Authenticated }
                .first()
        } != null

    private val baseUrl: String?
        get() = apiClient.serverBaseUrl.value

    private fun actionsForItem(itemId: String): List<MediaItem> {
        return buildList {
            add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setTitle("Play all")
                        .setMediaId(itemId)
                        .setIconUri(android.R.drawable.ic_media_play.toUri(context))
                        .setExtras(
                            Bundle().apply {
                                putString(
                                    MediaIds.QUEUE_OPTION_KEY,
                                    QueueOption.REPLACE.name,
                                )
                            },
                        )
                        .build(),
                    MediaItem.FLAG_PLAYABLE,
                ),
            )
            add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setTitle("Add all to queue")
                        .setMediaId(itemId)
                        .setIconUri(android.R.drawable.ic_menu_add.toUri(context))
                        .setExtras(
                            Bundle().apply {
                                putString(
                                    MediaIds.QUEUE_OPTION_KEY,
                                    QueueOption.ADD.name,
                                )
                            },
                        )
                        .build(),
                    MediaItem.FLAG_PLAYABLE,
                ),
            )
        }
    }

    fun search(
        query: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        result.detach()
        // converting to flow for filtering and debouncing
        searchFlow.update { Pair(query, result) }
    }

    fun searchAndPlay(query: String, queueId: String) {
        scope.launch {
            if (!waitForCorrectState()) return@launch
            val result = apiClient.sendRequest(
                Request.Library.search(
                    query = query,
                    mediaTypes = listOf(
                        MediaType.TRACK,
                        MediaType.ARTIST,
                        MediaType.ALBUM,
                        MediaType.PLAYLIST,
                    ),
                    libraryOnly = false,
                ),
            )
            val firstUri = result.resultAs<SearchResult>()?.let { sr ->
                sr.tracks.firstOrNull()?.uri
                    ?: sr.artists.firstOrNull()?.uri
                    ?: sr.albums.firstOrNull()?.uri
                    ?: sr.playlists.firstOrNull()?.uri
            } ?: return@launch
            apiClient.sendRequest(
                Request.Library.play(
                    media = listOf(firstUri),
                    queueOrPlayerId = queueId,
                    option = QueueOption.REPLACE,
                    radioMode = false,
                ),
            )
        }
    }

    fun play(id: String, extras: Bundle?, queueId: String) {
        id.split("__").getOrNull(1)?.let { uri ->
            scope.launch {
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(uri),
                        queueOrPlayerId = queueId,
                        option = extras?.getString(
                            MediaIds.QUEUE_OPTION_KEY,
                            QueueOption.REPLACE.name,
                        )?.let { QueueOption.valueOf(it) }
                            ?: QueueOption.REPLACE,
                        radioMode = false,
                    ),
                )
            }
        }
    }

    private fun rootTabItem(tabName: String, tabId: String): MediaItem =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(tabName)
                .setMediaId(tabId)
                .build(),
            MediaItem.FLAG_BROWSABLE,
        )

    private fun favoritesPseudoItem(favoritesId: String): MediaItem =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle("Favorites")
                .setMediaId(favoritesId)
                .setIconUri(R.drawable.baseline_favorite_24.toUri(context))
                .build(),
            MediaItem.FLAG_BROWSABLE,
        )

    private companion object {
        const val WAIT_FOR_AUTHENTICATED_TIMEOUT_MS = 30_000L
    }
}

private const val PARENT_REF_PARAMS_COUNT = 4

private const val PARENT_REF_ITEM_ID_PARAM_INDEX = 0
private const val PARENT_REF_URI_PARAM_INDEX = 1
private const val PARENT_REF_PROVIDER_PARAM_INDEX = 3

internal object MediaIds {
    const val ROOT = "auto_lib_root"
    const val TAB_ARTISTS = "auto_lib_artists"
    const val TAB_ALBUMS = "auto_lib_albums"
    const val TAB_PLAYLISTS = "auto_lib_playlists"
    const val TAB_PODCASTS = "auto_lib_podcasts"
    const val TAB_RADIO = "auto_lib_radio"
    const val TAB_AUDIOBOOKS = "auto_lib_audiobooks"
    const val QUEUE_OPTION_KEY = "auto_queue_option"

    const val FAVORITES_PREFIX = "auto_fav_"

    private val tabToType = mapOf(
        TAB_ARTISTS to MediaType.ARTIST,
        TAB_ALBUMS to MediaType.ALBUM,
        TAB_PLAYLISTS to MediaType.PLAYLIST,
        TAB_PODCASTS to MediaType.PODCAST,
        TAB_RADIO to MediaType.RADIO,
        TAB_AUDIOBOOKS to MediaType.AUDIOBOOK,
    )
    private val typeToTab = tabToType.entries.associate { (k, v) -> v to k }

    fun tabMediaTypeOf(id: String): MediaType? = tabToType[id]
    fun tabIdOf(type: MediaType): String = typeToTab.getValue(type)

    fun favoritesId(type: MediaType): String = "$FAVORITES_PREFIX${type.name}"
    fun favoritesMediaTypeOf(id: String): MediaType? =
        runCatching { MediaType.valueOf(id.removePrefix(FAVORITES_PREFIX)) }.getOrNull()
}

internal data class ParentRef(
    val itemId: String,
    val uri: String,
    val type: MediaType,
    val provider: String,
) {
    // Matches the existing 4-part `itemId__uri__mediaType__provider` encoding
    // produced in toMediaDescription, so parents discovered via drill-down IDs
    // stay round-trip-safe.
    fun encode(): String = "${itemId}__${uri}__${type}__$provider"

    fun subItemContext(): SubItemContext? = when (type) {
        MediaType.ARTIST -> SubItemContext.ARTIST_ALBUMS
        MediaType.ALBUM -> SubItemContext.ALBUM_TRACKS
        MediaType.PLAYLIST -> SubItemContext.PLAYLIST_TRACKS
        MediaType.PODCAST -> SubItemContext.PODCAST_EPISODES
        else -> null
    }

    companion object {
        fun parse(encoded: String): ParentRef? {
            val parts = encoded.split("__")
            if (parts.size != PARENT_REF_PARAMS_COUNT) return null
            val type = runCatching { MediaType.valueOf(parts[2]) }.getOrNull() ?: return null
            return ParentRef(
                parts[PARENT_REF_ITEM_ID_PARAM_INDEX],
                parts[PARENT_REF_URI_PARAM_INDEX],
                type,
                parts[PARENT_REF_PROVIDER_PARAM_INDEX],
            )
        }
    }
}

private fun SearchResult.toAutoMediaItems(
    serverUrl: String?,
    defaultIconUri: Uri,
): List<MediaItem> = buildList {
    mapOf(
        tracks to "Tracks",
        albums to "Albums",
        artists to "Artists",
        playlists to "Playlists",
        audiobooks to "Audiobooks",
        podcasts to "Podcasts",
        radio to "Radio stations",
    ).forEach { (items, category) ->
        addAll(items.mapNotNull { it.toAutoMediaItem(serverUrl, true, defaultIconUri, category) })
    }
}

private fun ServerMediaItem.toAutoMediaItem(
    serverUrl: String?,
    allowBrowse: Boolean,
    defaultIconUri: Uri,
    category: String? = null,
): MediaItem? =
    toAppMediaItem()?.toAutoMediaItem(serverUrl, allowBrowse, defaultIconUri, category)

private fun AppMediaItem.toAutoMediaItem(
    serverUrl: String?,
    allowBrowse: Boolean,
    defaultIconUri: Uri,
    category: String? = null,
): MediaItem {
    return MediaItem(
        toMediaDescription(serverUrl, defaultIconUri, category),
        if (allowBrowse && mediaType.isBrowsableInAuto()) {
            MediaItem.FLAG_BROWSABLE
        } else {
            MediaItem.FLAG_PLAYABLE
        },
    )
}

private fun MediaType.isBrowsableInAuto(): Boolean = this in setOf(
    MediaType.ARTIST, MediaType.ALBUM, MediaType.PODCAST, MediaType.PLAYLIST,
)

fun @receiver:DrawableRes Int.toUri(context: Context): Uri = Uri.parse(
    ContentResolver.SCHEME_ANDROID_RESOURCE +
            "://" + context.resources.getResourcePackageName(this) +
            '/' + context.resources.getResourceTypeName(this) +
            '/' + context.resources.getResourceEntryName(this),
)

fun AppMediaItem.toMediaDescription(
    serverUrl: String?,
    defaultIconUri: Uri,
    category: String? = null,
): MediaDescriptionCompat {
    return MediaDescriptionCompat.Builder()
        .setMediaId("${itemId}__${uri}__${mediaType}__$provider")
        .setTitle((if (favorite == true) "♥ " else "") + title)
        .setSubtitle(subtitle)
        .setMediaUri(uri?.let { Uri.parse(it) })
        .setIconUri(imageInfo?.url(serverUrl)?.let { Uri.parse(it) } ?: defaultIconUri)
        .setExtras(
            Bundle().apply {
                putString(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                    category,
                )
            },
        )
        .build()
}
