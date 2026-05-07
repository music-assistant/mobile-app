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
import io.music_assistant.client.settings.SettingsRepository
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
    private val settings: SettingsRepository,
) {
    // Service supplies its own notifyChildrenChanged so the cached parent view
    // (with the now-stale "Sort by: <old>" subtitle) is invalidated after a
    // sort selection is persisted. Set once during service onCreate.
    var notifyChildrenChanged: ((parentId: String) -> Unit)? = null

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

            id.startsWith(MediaIds.SORT_MENU_PREFIX) -> handleSortMenu(id, result)
            id.startsWith(MediaIds.SORT_APPLY_PREFIX) -> handleSortApply(id, result)
            id.startsWith(MediaIds.FAVORITES_PREFIX) -> handleFavorites(id, result)
            id.startsWith(MediaIds.SUB_SORT_MENU_PREFIX) -> handleSubSortMenu(id, result)
            id.startsWith(MediaIds.SUB_SORT_APPLY_PREFIX) -> handleSubSortApply(id, result)
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
            val sort = settings.getAutoSortOption(mediaType)
            val items = loadTabItems(mediaType, sort, favoritesOnly) ?: run {
                result.sendResult(null)
                return@launch
            }
            val header = if (favoritesOnly) {
                emptyList()
            } else {
                listOf(
                    sortByPseudoItem(
                        MediaIds.sortMenuId(mediaType),
                        AutoSortPresets.labelOf(mediaType, sort),
                    ),
                    favoritesPseudoItem(MediaIds.favoritesId(mediaType)),
                )
            }
            result.sendResult(header + items)
        }
    }

    private fun handleSortMenu(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val mediaType = MediaIds.sortMenuMediaTypeOf(id) ?: run {
            result.sendResult(null)
            return
        }
        val current = settings.getAutoSortOption(mediaType)
        val presets = AutoSortPresets.byTab[mediaType] ?: run {
            result.sendResult(null)
            return
        }
        val items = presets.map { preset ->
            sortPresetItem(
                presetId = MediaIds.sortApplyId(mediaType, preset.option),
                title = preset.label,
                isCurrent = preset.option == current,
            )
        }
        result.sendResult(items)
    }

    private fun handleSortApply(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val (mediaType, sort) = MediaIds.parseSortApply(id) ?: run {
            result.sendResult(null)
            return
        }
        // Idempotent: only persist + invalidate cache when the sort actually changes.
        // Without this, any AA re-subscribe to an apply node (which can happen when AA
        // refreshes the browse stack for *any* reason — e.g. session-state churn,
        // playback-state updates, host re-render) would re-invoke notifyChildrenChanged
        // on the parent tab, AA re-renders the tab, the user's scroll resets, and the
        // browse view glitches many times a second.
        if (settings.getAutoSortOption(mediaType) != sort) {
            settings.setAutoSortOption(mediaType, sort)
            notifyChildrenChanged?.invoke(MediaIds.tabIdOf(mediaType))
        }
        result.detach()
        scope.launch {
            if (!waitForCorrectState()) {
                result.sendResult(null)
                return@launch
            }
            result.sendResult(loadTabItems(mediaType, sort, favoritesOnly = false))
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

    private fun handleSubSortMenu(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val parsed = MediaIds.parseSubSortMenu(id) ?: run {
            result.sendResult(null)
            return
        }
        val current = settings.getAutoSortOption(parsed.context)
        val presets = AutoSortPresets.bySubContext[parsed.context] ?: run {
            result.sendResult(null)
            return
        }
        val items = presets.map { preset ->
            sortPresetItem(
                presetId = MediaIds.subSortApplyId(parsed.context, preset.option, parsed.parent),
                title = preset.label,
                isCurrent = preset.option == current,
            )
        }
        result.sendResult(items)
    }

    private fun handleSubSortApply(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val parsed = MediaIds.parseSubSortApply(id) ?: run {
            result.sendResult(null)
            return
        }
        if (settings.getAutoSortOption(parsed.context) != parsed.option) {
            settings.setAutoSortOption(parsed.context, parsed.option)
            notifyChildrenChanged?.invoke(parsed.parent.encode())
        }
        result.detach()
        scope.launch {
            val list = loadSubItems(parsed.context, parsed.parent, parsed.option)
            result.sendResult(list)
        }
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
            val sort = settings.getAutoSortOption(context)
            val items = loadSubItems(context, parent, sort) ?: run {
                result.sendResult(null)
                return@launch
            }
            val header = buildList {
                if (context != SubItemContext.ALBUM_TRACKS) {
                    add(
                        sortByPseudoItem(
                            MediaIds.subSortMenuId(context, parent),
                            AutoSortPresets.labelOf(context, sort),
                        ),
                    )
                }
                addAll(actionsForItem(id))
            }
            result.sendResult(header + items)
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

    private fun sortByPseudoItem(menuId: String, currentLabel: String): MediaItem =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle("Sort by")
                .setSubtitle(currentLabel)
                .setMediaId(menuId)
                .setIconUri(android.R.drawable.ic_menu_sort_by_size.toUri(context))
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

    private fun sortPresetItem(presetId: String, title: String, isCurrent: Boolean): MediaItem =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(if (isCurrent) "✓ $title" else title)
                .setMediaId(presetId)
                .build(),
            MediaItem.FLAG_BROWSABLE,
        )

    private companion object {
        const val WAIT_FOR_AUTHENTICATED_TIMEOUT_MS = 30_000L
    }
}

private const val SORT_APPLY_PARAMS_COUNT = 3
private const val PARENT_REF_PARAMS_COUNT = 4
private const val SUB_SORT_APPLY_PARAMS_COUNT = 4

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

    const val SORT_MENU_PREFIX = "auto_sortmenu_"
    const val SORT_APPLY_PREFIX = "auto_sortapply_"
    const val FAVORITES_PREFIX = "auto_fav_"
    const val SUB_SORT_MENU_PREFIX = "auto_subsortmenu_"
    const val SUB_SORT_APPLY_PREFIX = "auto_subsortapply_"

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

    fun sortMenuId(type: MediaType): String = "$SORT_MENU_PREFIX${type.name}"
    fun sortMenuMediaTypeOf(id: String): MediaType? =
        runCatching { MediaType.valueOf(id.removePrefix(SORT_MENU_PREFIX)) }.getOrNull()

    fun sortApplyId(type: MediaType, option: SortOption): String =
        "$SORT_APPLY_PREFIX${type.name}|${option.field.name}|${option.descending}"

    fun parseSortApply(id: String): Pair<MediaType, SortOption>? {
        val parts = id.removePrefix(SORT_APPLY_PREFIX).split("|")
        if (parts.size != SORT_APPLY_PARAMS_COUNT) return null
        val type = runCatching { MediaType.valueOf(parts[0]) }.getOrNull() ?: return null
        val field = runCatching { SortField.valueOf(parts[1]) }.getOrNull() ?: return null
        val desc = parts[2].toBooleanStrictOrNull() ?: return null
        return type to SortOption(field, desc)
    }

    fun favoritesId(type: MediaType): String = "$FAVORITES_PREFIX${type.name}"
    fun favoritesMediaTypeOf(id: String): MediaType? =
        runCatching { MediaType.valueOf(id.removePrefix(FAVORITES_PREFIX)) }.getOrNull()

    fun subSortMenuId(context: SubItemContext, parent: ParentRef): String =
        "$SUB_SORT_MENU_PREFIX${context.name}|${parent.encode()}"

    fun parseSubSortMenu(id: String): SubSortMenuRef? {
        val rest = id.removePrefix(SUB_SORT_MENU_PREFIX)
        val sepIdx = rest.indexOf('|').takeIf { it >= 0 } ?: return null
        val ctx = runCatching { SubItemContext.valueOf(rest.substring(0, sepIdx)) }.getOrNull()
            ?: return null
        val parent = ParentRef.parse(rest.substring(sepIdx + 1)) ?: return null
        return SubSortMenuRef(ctx, parent)
    }

    fun subSortApplyId(context: SubItemContext, option: SortOption, parent: ParentRef): String =
        "$SUB_SORT_APPLY_PREFIX${context.name}|${option.field.name}|${option.descending}|${parent.encode()}"

    fun parseSubSortApply(id: String): SubSortApplyRef? {
        val rest = id.removePrefix(SUB_SORT_APPLY_PREFIX)
        val parts = rest.split("|", limit = 4)
        if (parts.size != SUB_SORT_APPLY_PARAMS_COUNT) return null
        val ctx = runCatching { SubItemContext.valueOf(parts[0]) }.getOrNull() ?: return null
        val field = runCatching { SortField.valueOf(parts[1]) }.getOrNull() ?: return null
        val desc = parts[2].toBooleanStrictOrNull() ?: return null
        val parent = ParentRef.parse(parts[3]) ?: return null
        return SubSortApplyRef(ctx, SortOption(field, desc), parent)
    }
}

internal data class ParentRef(
    val itemId: String,
    val uri: String,
    val type: MediaType,
    val provider: String,
) {
    // Matches the existing 4-part `itemId__uri__mediaType__provider` encoding
    // produced in toMediaDescription, so parents discovered via drill-down IDs
    // and re-encoded for sort sub-menus stay round-trip-safe.
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

internal data class SubSortMenuRef(val context: SubItemContext, val parent: ParentRef)
internal data class SubSortApplyRef(
    val context: SubItemContext,
    val option: SortOption,
    val parent: ParentRef,
)

private object AutoSortPresets {
    data class Preset(val label: String, val option: SortOption)

    private fun asc(field: SortField, label: String) =
        Preset(label, SortOption(field, descending = false))

    private fun desc(field: SortField, label: String) =
        Preset(label, SortOption(field, descending = true))

    val byTab: Map<MediaType, List<Preset>> = mapOf(
        MediaType.ARTIST to listOf(
            asc(SortField.NAME, "A–Z"),
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.LAST_PLAYED, "Recently played"),
            desc(SortField.PLAY_COUNT, "Most played"),
        ),
        MediaType.ALBUM to listOf(
            asc(SortField.NAME, "A–Z"),
            asc(SortField.ARTIST_NAME, "By artist"),
            desc(SortField.YEAR, "Newest"),
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.LAST_PLAYED, "Recently played"),
            desc(SortField.PLAY_COUNT, "Most played"),
        ),
        MediaType.PLAYLIST to listOf(
            asc(SortField.NAME, "A–Z"),
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.DATE_MODIFIED, "Recently modified"),
            desc(SortField.LAST_PLAYED, "Recently played"),
            desc(SortField.PLAY_COUNT, "Most played"),
        ),
        MediaType.PODCAST to listOf(
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.DATE_MODIFIED, "Recently updated"),
            asc(SortField.NAME, "A–Z"),
            desc(SortField.LAST_PLAYED, "Recently played"),
        ),
        MediaType.RADIO to listOf(
            asc(SortField.NAME, "A–Z"),
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.LAST_PLAYED, "Recently played"),
            desc(SortField.PLAY_COUNT, "Most played"),
        ),
        MediaType.AUDIOBOOK to listOf(
            asc(SortField.NAME, "A–Z"),
            desc(SortField.DATE_ADDED, "Recently added"),
            desc(SortField.LAST_PLAYED, "Recently played"),
        ),
    )

    val bySubContext: Map<SubItemContext, List<Preset>> = mapOf(
        SubItemContext.ARTIST_ALBUMS to listOf(
            desc(SortField.YEAR, "Newest"),
            asc(SortField.YEAR, "Oldest"),
            asc(SortField.NAME, "A–Z"),
        ),
        SubItemContext.PLAYLIST_TRACKS to listOf(
            asc(SortField.ORIGINAL, "Original"),
            asc(SortField.NAME, "A–Z"),
            asc(SortField.ARTIST_NAME, "By artist"),
        ),
        SubItemContext.PODCAST_EPISODES to listOf(
            desc(SortField.RELEASE_DATE, "Newest"),
            asc(SortField.RELEASE_DATE, "Oldest"),
            asc(SortField.NAME, "A–Z"),
        ),
    )

    fun labelOf(type: MediaType, option: SortOption): String =
        byTab[type]?.firstOrNull { it.option == option }?.label ?: option.field.displayName

    fun labelOf(context: SubItemContext, option: SortOption): String =
        bySubContext[context]?.firstOrNull { it.option == option }?.label
            ?: option.field.displayName
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
