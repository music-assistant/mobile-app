package io.music_assistant.client.auto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import co.touchlab.kermit.Logger
import io.music_assistant.client.R
import io.music_assistant.client.api.Answer
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
import io.music_assistant.client.ui.compose.library.LibraryViewModel
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
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class)
class AutoLibrary(
    private val context: Context,
    private val apiClient: ServiceClient,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val searchFlow: MutableStateFlow<Pair<String, MediaBrowserServiceCompat.Result<List<MediaItem>>>?> =
        MutableStateFlow(null)
    private val defaultIconUri = R.drawable.baseline_library_music_24.toUri(context)

    // Dedicated tag for voice-search debugging. Filter logcat with `AAVoice:V *:S`
    // to isolate the entire EXTRA_MEDIA_FOCUS → search → play pipeline.
    private val voiceLog = Logger.withTag("AAVoice")

    // AA hosts re-subscribe browse parents aggressively (on metadata / playback /
    // focus changes). With 27 browsable nodes (6 tabs + 5*4 sub-lists + radio
    // header) every reconnect would otherwise issue 21 server list requests in
    // one burst. Cache is invalidated explicitly from the service on reconnect.
    private val itemCache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTtlMs = 5 * 60_000L

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
            MediaIds.parseSubListId(id) != null -> handleSubList(id, result)
            MediaIds.tabMediaTypeOf(id) != null -> handleTabContent(id, result)
            else -> handleDrillDown(id, result)
        }
    }

    fun invalidateCache() {
        itemCache.clear()
    }

    // Default order/visibility when the user hasn't customized library tabs in
    // the phone UI. Tracks/Genres are intentionally not exposed in AA.
    private val defaultAutoTabs: List<Pair<MediaType, String>> = listOf(
        MediaType.ARTIST to "Artists",
        MediaType.ALBUM to "Albums",
        MediaType.PLAYLIST to "Playlists",
        MediaType.PODCAST to "Podcasts",
        MediaType.RADIO to "Radio",
        MediaType.AUDIOBOOK to "Audiobooks",
    )

    private fun rootChildren(): List<MediaItem> {
        val titles = defaultAutoTabs.toMap()
        val supportedTypes = titles.keys
        val stored = settingsRepository.libraryTabsConfig.value
        val ordered: List<MediaType> = if (stored == null) {
            defaultAutoTabs.map { it.first }
        } else {
            stored.mapNotNull { pref ->
                if (!pref.enabled) return@mapNotNull null
                val tab = runCatching { LibraryViewModel.Tab.valueOf(pref.name) }.getOrNull()
                    ?: return@mapNotNull null
                tab.mediaType.takeIf { it in supportedTypes }
            }
        }
        return ordered.map { type -> rootTabItem(titles.getValue(type), MediaIds.tabIdOf(type)) }
    }

    private fun handleTabContent(
        tabId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val mediaType = MediaIds.tabMediaTypeOf(tabId) ?: run {
            result.sendResult(null)
            return
        }
        if (mediaType != MediaType.RADIO) {
            // Collection tabs return only stateless sub-list browsables. The
            // selection is encoded in the parentId — no settings, no parent
            // invalidation, no re-fetch loop.
            result.sendResult(AutoSubList.entries.map { subListBrowsable(mediaType, it) })
            return
        }
        // Radio: a flat list (sorted by recently played) with a single
        // Favorites browsable header.
        result.detach()
        scope.launch {
            val items = cachedOrFetch(tabId) {
                if (!waitForCorrectState()) return@cachedOrFetch null
                val sorted = loadTabItems(
                    MediaType.RADIO,
                    SortOption(SortField.LAST_PLAYED, descending = true),
                    favoritesOnly = false,
                ) ?: return@cachedOrFetch null
                listOf(subListBrowsable(MediaType.RADIO, AutoSubList.FAVORITES)) + sorted
            }
            result.sendResult(items)
        }
    }

    private fun handleSubList(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
    ) {
        val (mediaType, key) = MediaIds.parseSubListId(id) ?: run {
            result.sendResult(null)
            return
        }
        val spec = subListSpec(mediaType, key) ?: run {
            result.sendResult(null)
            return
        }
        result.detach()
        scope.launch {
            val items = cachedOrFetch(id) {
                if (!waitForCorrectState()) return@cachedOrFetch null
                loadTabItems(mediaType, spec.sort, spec.favoritesOnly)
            }
            result.sendResult(items)
        }
    }

    private suspend fun cachedOrFetch(
        cacheKey: String,
        fetch: suspend () -> List<MediaItem>?,
    ): List<MediaItem>? {
        val now = System.currentTimeMillis()
        itemCache[cacheKey]?.let { (cached, ts) ->
            if (now - ts < cacheTtlMs) return cached
        }
        val items = fetch() ?: return null
        itemCache[cacheKey] = CacheEntry(items, now)
        return items
    }

    private data class SubListSpec(val sort: SortOption, val favoritesOnly: Boolean)

    private fun subListSpec(type: MediaType, key: AutoSubList): SubListSpec? {
        // Radio only exposes FAVORITES as a sub-list; other keys are not valid.
        if (type == MediaType.RADIO && key != AutoSubList.FAVORITES) return null
        return when (key) {
            AutoSubList.RECENT ->
                SubListSpec(SortOption(SortField.LAST_PLAYED, descending = true), false)

            AutoSubList.FAVORITES ->
                SubListSpec(SortOption(SortField.NAME), true)

            AutoSubList.NEW -> if (type == MediaType.PODCAST) {
                // For podcasts, "New" means recently updated (i.e. has new episodes).
                SubListSpec(SortOption(SortField.DATE_MODIFIED, descending = true), false)
            } else {
                SubListSpec(SortOption(SortField.DATE_ADDED, descending = true), false)
            }

            AutoSubList.BY_NAME ->
                SubListSpec(SortOption(SortField.NAME), false)
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

    fun searchAndPlay(query: String, extras: Bundle?, queueId: String) {
        scope.launch {
            val ready = waitForCorrectState()
            if (!ready) {
                voiceLog.w {
                    "Server not authenticated within ${WAIT_FOR_AUTHENTICATED_TIMEOUT_MS}ms — " +
                        "aborting voice playback (query=\"$query\")."
                }
                return@launch
            }
            // MediaStore.Audio.{Artists,Albums,Media,Playlists,Genres}.ENTRY_CONTENT_TYPE
            // are deprecated in MediaStore itself but remain the canonical EXTRA_MEDIA_FOCUS
            // values Google Assistant emits, with no documented replacement. Suppress here
            // so the warnings don't bury real ones.
            @Suppress("DEPRECATION")
            val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
            voiceLog.i {
                @Suppress("DEPRECATION")
                val artistExtra = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)

                @Suppress("DEPRECATION")
                val albumExtra = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)

                @Suppress("DEPRECATION")
                val titleExtra = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)

                @Suppress("DEPRECATION")
                val playlistExtra = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)

                @Suppress("DEPRECATION")
                val genreExtra = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)
                "searchAndPlay focus=$focus query=\"$query\" queueId=$queueId " +
                    "extras{artist=$artistExtra album=$albumExtra title=$titleExtra " +
                    "playlist=$playlistExtra genre=$genreExtra}"
            }
            @Suppress("DEPRECATION")
            when (focus) {
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE ->
                    playArtist(
                        artistName = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST) ?: query,
                        queueId = queueId,
                    )

                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE ->
                    playAlbum(
                        albumName = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM) ?: query,
                        artistName = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST),
                        queueId = queueId,
                    )

                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE ->
                    playTrack(
                        title = extras.getString(MediaStore.EXTRA_MEDIA_TITLE) ?: query,
                        artistName = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST),
                        queueId = queueId,
                    )

                MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE ->
                    playPlaylist(
                        playlistName = extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST) ?: query,
                        queueId = queueId,
                    )

                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    voiceLog.i { "Genre focus → unstructured search on genre keyword" }
                    playUnstructured(
                        query = extras.getString(MediaStore.EXTRA_MEDIA_GENRE) ?: query,
                        queueId = queueId,
                    )
                }

                else -> if (query.isBlank()) {
                    voiceLog.i { "No focus + blank query → random favorites" }
                    playRandomFavorites(queueId)
                } else {
                    voiceLog.i { "No focus + non-blank query → unstructured cascade" }
                    playUnstructured(query, queueId)
                }
            }
        }
    }

    private suspend fun playArtist(artistName: String, queueId: String) {
        voiceLog.i { "playArtist(\"$artistName\")" }
        val sr = sendLogged("search ARTIST=\"$artistName\"") {
            Request.Library.search(
                query = artistName,
                mediaTypes = listOf(MediaType.ARTIST),
                libraryOnly = false,
            )
        }?.resultAs<SearchResult>()
        voiceLog.i { "  → artists found: ${sr?.artists?.size ?: 0}" }
        val artist = sr?.artists?.firstOrNull()
        if (artist == null) {
            voiceLog.i { "No artist match for \"$artistName\" — nothing to play." }
            return
        }
        voiceLog.i {
            "  → matched artist=${artist.name} item_id=${artist.itemId} provider=${artist.provider} uri=${artist.uri}"
        }
        playArtistTracksShuffled(artist, queueId)
    }

    private suspend fun playAlbum(albumName: String, artistName: String?, queueId: String) {
        voiceLog.i { "playAlbum(\"$albumName\", artist=\"$artistName\")" }
        val combinedQuery = listOfNotNull(albumName, artistName).joinToString(" ")
        val sr = sendLogged("search ALBUM=\"$combinedQuery\"") {
            Request.Library.search(
                query = combinedQuery,
                mediaTypes = listOf(MediaType.ALBUM),
                libraryOnly = false,
            )
        }?.resultAs<SearchResult>()
        voiceLog.i { "  → albums found: ${sr?.albums?.size ?: 0}" }
        val match = artistName?.let { wanted ->
            sr?.albums?.firstOrNull { album ->
                album.artists?.any { it.name.equals(wanted, ignoreCase = true) } == true
            }
        } ?: sr?.albums?.firstOrNull()
        val uri = match?.uri
        if (uri == null) {
            voiceLog.i { "No album match (refined-by-artist=${artistName != null}) — nothing to play." }
            return
        }
        voiceLog.i { "  → matched album uri=$uri" }
        playUris(listOf(uri), queueId)
    }

    private suspend fun playTrack(title: String, artistName: String?, queueId: String) {
        voiceLog.i { "playTrack(\"$title\", artist=\"$artistName\")" }
        val combinedQuery = listOfNotNull(title, artistName).joinToString(" ")
        val sr = sendLogged("search TRACK=\"$combinedQuery\"") {
            Request.Library.search(
                query = combinedQuery,
                mediaTypes = listOf(MediaType.TRACK),
                libraryOnly = false,
            )
        }?.resultAs<SearchResult>()
        voiceLog.i { "  → tracks found: ${sr?.tracks?.size ?: 0}" }
        val match = artistName?.let { wanted ->
            sr?.tracks?.firstOrNull { track ->
                track.artists?.any { it.name.equals(wanted, ignoreCase = true) } == true
            }
        } ?: sr?.tracks?.firstOrNull()
        val uri = match?.uri
        if (uri == null) {
            voiceLog.i { "No track match (refined-by-artist=${artistName != null}) — nothing to play." }
            return
        }
        voiceLog.i { "  → matched track uri=$uri" }
        playUris(listOf(uri), queueId)
    }

    private suspend fun playPlaylist(playlistName: String, queueId: String) {
        voiceLog.i { "playPlaylist(\"$playlistName\")" }
        val sr = sendLogged("search PLAYLIST=\"$playlistName\"") {
            Request.Library.search(
                query = playlistName,
                mediaTypes = listOf(MediaType.PLAYLIST),
                libraryOnly = false,
            )
        }?.resultAs<SearchResult>()
        voiceLog.i { "  → playlists found: ${sr?.playlists?.size ?: 0}" }
        val uri = sr?.playlists?.firstOrNull()?.uri
        if (uri == null) {
            voiceLog.i { "No playlist match — nothing to play." }
            return
        }
        voiceLog.i { "  → matched playlist uri=$uri" }
        playUris(listOf(uri), queueId)
    }

    private suspend fun playUnstructured(query: String, queueId: String) {
        voiceLog.i { "playUnstructured(\"$query\")" }
        val sr = sendLogged("search UNSTRUCTURED=\"$query\"") {
            Request.Library.search(
                query = query,
                mediaTypes = listOf(
                    MediaType.TRACK,
                    MediaType.ARTIST,
                    MediaType.ALBUM,
                    MediaType.PLAYLIST,
                    MediaType.PODCAST,
                    MediaType.RADIO,
                ),
                libraryOnly = false,
            )
        }?.resultAs<SearchResult>()
        if (sr == null) {
            voiceLog.w { "Unstructured search returned null result — nothing to play." }
            return
        }
        voiceLog.i {
            "  → hits: tracks=${sr.tracks.size} artists=${sr.artists.size} " +
                "albums=${sr.albums.size} playlists=${sr.playlists.size} " +
                "podcasts=${sr.podcasts.size} radio=${sr.radio.size}"
        }
        // Track → Artist → Album → Playlist → Podcast → Radio. Artist match expands to
        // shuffled discography; podcast match plays latest episode; everything else
        // is queued by its own URI.
        sr.tracks.firstOrNull()?.uri?.let {
            voiceLog.i { "  → picked TRACK uri=$it" }
            playUris(listOf(it), queueId)
            return
        }
        sr.artists.firstOrNull()?.let {
            voiceLog.i { "  → picked ARTIST name=${it.name} item_id=${it.itemId} provider=${it.provider}" }
            playArtistTracksShuffled(it, queueId)
            return
        }
        sr.albums.firstOrNull()?.uri?.let {
            voiceLog.i { "  → picked ALBUM uri=$it" }
            playUris(listOf(it), queueId)
            return
        }
        sr.playlists.firstOrNull()?.uri?.let {
            voiceLog.i { "  → picked PLAYLIST uri=$it" }
            playUris(listOf(it), queueId)
            return
        }
        sr.podcasts.firstOrNull()?.let {
            voiceLog.i { "  → picked PODCAST item_id=${it.itemId} provider=${it.provider}" }
            playPodcastLatest(it, queueId)
            return
        }
        sr.radio.firstOrNull()?.uri?.let {
            voiceLog.i { "  → picked RADIO uri=$it" }
            playUris(listOf(it), queueId)
            return
        }
        voiceLog.i { "Unstructured search for \"$query\" produced zero hits across all media types." }
    }

    private suspend fun playArtistTracksShuffled(artist: ServerMediaItem, queueId: String) {
        val trackResult = sendLogged("Artist.getTracks item_id=${artist.itemId} provider=${artist.provider}") {
            Request.Artist.getTracks(artist.itemId, artist.provider)
        }
        val trackUris = trackResult?.resultAs<List<ServerMediaItem>>()
            ?.mapNotNull { it.uri }
            ?.shuffled()
            .orEmpty()
        voiceLog.i { "  → artist track count: ${trackUris.size}" }
        val media = trackUris.takeIf { it.isNotEmpty() } ?: listOfNotNull(artist.uri).also {
            if (it.isNotEmpty()) {
                voiceLog.i {
                    "Artist.getTracks returned empty — falling back to artist URI ${artist.uri}. " +
                        "Server expansion of artist URIs is provider-dependent."
                }
            }
        }
        if (media.isEmpty()) {
            voiceLog.i { "Artist has no playable URIs (tracks empty AND artist.uri null)." }
            return
        }
        playAndShuffle(media, queueId, shuffle = true)
    }

    private suspend fun playPodcastLatest(podcast: ServerMediaItem, queueId: String) {
        val episodes = sendLogged("Podcast.getEpisodes item_id=${podcast.itemId} provider=${podcast.provider}") {
            Request.Podcast.getEpisodes(podcast.itemId, podcast.provider)
        }?.resultAs<List<ServerMediaItem>>()
        voiceLog.i { "  → episode count: ${episodes?.size ?: 0}" }
        val latestUri = episodes?.maxByOrNull { it.metadata?.releaseDate.orEmpty() }?.uri
            ?: podcast.uri
        if (latestUri == null) {
            voiceLog.i { "Podcast has no playable URIs (no episodes AND podcast.uri null)." }
            return
        }
        voiceLog.i { "  → playing podcast uri=$latestUri" }
        playUris(listOf(latestUri), queueId)
    }

    private suspend fun playRandomFavorites(queueId: String) {
        voiceLog.i { "playRandomFavorites" }
        val favoriteUris = sendLogged("Track.list favorite=true limit=$RANDOM_POOL_SIZE") {
            Request.Track.list(favorite = true, limit = RANDOM_POOL_SIZE)
        }?.resultAs<List<ServerMediaItem>>()?.mapNotNull { it.uri }.orEmpty()
        voiceLog.i { "  → favorite tracks: ${favoriteUris.size}" }
        val pool = favoriteUris.ifEmpty {
            voiceLog.i { "  → favorites empty, falling back to last_played_desc" }
            sendLogged("Track.list orderBy=last_played_desc limit=$RANDOM_POOL_SIZE") {
                Request.Track.list(orderBy = "last_played_desc", limit = RANDOM_POOL_SIZE)
            }?.resultAs<List<ServerMediaItem>>()?.mapNotNull { it.uri }.orEmpty()
        }
        if (pool.isEmpty()) {
            voiceLog.i { "No favorites AND no recently-played tracks — random fallback has no pool." }
            return
        }
        playAndShuffle(pool.shuffled(), queueId, shuffle = true)
    }

    private suspend fun playUris(media: List<String>, queueId: String) {
        if (media.isEmpty()) {
            voiceLog.w { "playUris called with empty media list — no-op." }
            return
        }
        voiceLog.i {
            "Library.play REPLACE queueId=$queueId items=${media.size} first=${media.first()}"
        }
        sendLogged("Library.play (${media.size} items)") {
            Request.Library.play(
                media = media,
                queueOrPlayerId = queueId,
                option = QueueOption.REPLACE,
                radioMode = false,
            )
        }
    }

    private suspend fun playAndShuffle(media: List<String>, queueId: String, shuffle: Boolean) {
        playUris(media, queueId)
        if (shuffle) {
            voiceLog.i { "Queue.setShuffle enabled=true queueId=$queueId" }
            sendLogged("Queue.setShuffle enabled=true") {
                Request.Queue.setShuffle(queueId = queueId, enabled = true)
            }
        }
    }

    /**
     * Wrapper around [ServiceClient.sendRequest] that logs RPC failures. The RPC layer
     * returns `kotlin.Result<Answer>`; on failure the `Answer` is unavailable and the
     * caller would otherwise see a silent `null` decode downstream — that's the exact
     * gap voice debugging needs filled.
     */
    private suspend fun sendLogged(label: String, request: () -> Request): Answer? =
        apiClient.sendRequest(request()).fold(
            onSuccess = { answer ->
                voiceLog.d { "[$label] ok message_id=${answer.messageId}" }
                answer
            },
            onFailure = { t ->
                voiceLog.w(t) { "[$label] RPC failure: ${t.message}" }
                null
            },
        )

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

    private fun subListBrowsable(mediaType: MediaType, key: AutoSubList): MediaItem {
        val title = when (key) {
            AutoSubList.RECENT -> "Recent"
            AutoSubList.FAVORITES -> "Favorites"
            AutoSubList.NEW -> "New"
            AutoSubList.BY_NAME -> "By name"
        }
        val icon = when (key) {
            AutoSubList.FAVORITES -> R.drawable.baseline_favorite_24.toUri(context)
            else -> defaultIconUri
        }
        return MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(title)
                .setMediaId(MediaIds.subListIdOf(mediaType, key))
                .setIconUri(icon)
                .build(),
            MediaItem.FLAG_BROWSABLE,
        )
    }

    private companion object {
        const val WAIT_FOR_AUTHENTICATED_TIMEOUT_MS = 30_000L

        // Random-favorites pool size. 200 keeps the shuffle interesting without
        // overloading the play_media RPC payload for users with large libraries.
        const val RANDOM_POOL_SIZE = 200
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

    // `_` is taken by tab IDs and `__` by ParentRef; `|` keeps sub-list IDs
    // unambiguous against both (and against keys like BY_NAME that contain `_`).
    // Avoid `#` — some AA components treat mediaIds as URIs and `#` is the
    // fragment delimiter.
    private const val SUBLIST_SEP = '|'

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

    fun subListIdOf(type: MediaType, key: AutoSubList): String =
        "${tabIdOf(type)}$SUBLIST_SEP${key.name}"

    fun parseSubListId(id: String): Pair<MediaType, AutoSubList>? {
        val idx = id.indexOf(SUBLIST_SEP).takeIf { it >= 0 } ?: return null
        val type = tabMediaTypeOf(id.substring(0, idx)) ?: return null
        val key = runCatching { AutoSubList.valueOf(id.substring(idx + 1)) }.getOrNull()
            ?: return null
        return type to key
    }
}

internal enum class AutoSubList { RECENT, FAVORITES, NEW, BY_NAME }

internal data class CacheEntry(val items: List<MediaItem>, val timestamp: Long)

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
        .setTitle((if (favorite == true) "♥ " else "") + displayName)
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
