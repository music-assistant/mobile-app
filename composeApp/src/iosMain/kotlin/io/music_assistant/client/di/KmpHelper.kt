@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.music_assistant.client.di

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.auth.OAuthCallback
import io.music_assistant.client.carplay.CarPlayStrings
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.NowPlayingModes
import io.music_assistant.client.data.NowPlayingTrack
import io.music_assistant.client.data.NowPlayingTransport
import io.music_assistant.client.data.executeLocalPlayerDispatch
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.toItemKind
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.planLocalPlayerDispatch
import io.music_assistant.client.data.repository.AiRadioRepository
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.settings.CarPlatform
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.carBulkActions
import io.music_assistant.client.settings.carTapAction
import io.music_assistant.client.settings.planCarItemDispatch
import io.music_assistant.client.settings.toCarDispatch
import io.music_assistant.client.ui.compose.library.reconcileCarTabs
import io.music_assistant.client.ui.compose.library.visibleCategories
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.LocalNetworkPermissionGate
import io.music_assistant.client.utils.LocalNetworkPermissionProber
import io.music_assistant.client.utils.currentTimeMillis
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import platform.Foundation.NSData
import platform.Foundation.create

private val log = Logger.withTag("KmpHelper")

/** Swift-callable cancellation handle for coroutine subscriptions. */
fun interface Cancellable { fun cancel() }

/** CarPlay round-trip budget before a fetch surfaces a disconnected affordance. */
private const val FETCH_TIMEOUT_MS = 5_000L

/**
 * KmpHelper - Bridge for accessing Koin dependencies from Swift
 */
object KmpHelper : KoinComponent {
    val mainDataSource: MainDataSource by inject()
    val serviceClient: ServiceClient by inject()
    val authManager: AuthenticationManager by inject()
    private val deepLinkBus: DeepLinkBus by inject()
    private val mediaItemRepository: MediaItemRepository by inject()
    private val aiRadioRepository: AiRadioRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val volumeButtonService: VolumeButtonService by inject()
    private val artworkHttpClient: HttpClient by inject(named("webrtcHttpClient"))

    /**
     * Local Network permission gate; iOS replaces the default prober at bootstrap
     * with the Swift NWBrowser probe (see `LocalNetworkProbe.swift`).
     */
    val localNetworkPermissionGate: LocalNetworkPermissionGate by inject()

    fun setLocalNetworkPermissionProber(prober: LocalNetworkPermissionProber) {
        localNetworkPermissionGate.setProber(prober)
    }

    // Provide a scope for Swift to launch coroutines if needed
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The connected MA server's stable identifier (UUID-style, e.g.
     * "70e548..."). Available once the server has sent its handshake;
     * null while the connection is still being established.
     */
    fun getServerId(): String? {
        return (serviceClient.sessionState.value as? HasConnectionData)
            ?.connectionData
            ?.serverInfo
            ?.serverId
    }

    /**
     * Route a `musicassistant://app/<page>` URL into the navigation deep-link
     * bus. Parsing/validation lives in [DeepLinkBus]; non-matching URLs are
     * silently ignored.
     */
    fun handleDeepLink(urlString: String) = deepLinkBus.handle(urlString)

    /**
     * Callback scheme for `ASWebAuthenticationSession`, so Swift does not hold its own
     * copy that could drift from the redirect URL the server is given. Bare scheme, not
     * a URL: the session matches on the scheme alone.
     */
    fun oauthCallbackScheme(): String = OAuthCallback.SCHEME

    fun onPlatformVolumeButtonPressed() {
        volumeButtonService.onPlatformVolumeButtonPressed()
    }

    // MARK: - External Consumer Lifecycle (CarPlay)

    /**
     * Resolve CarPlay UI strings for the current locale off the shared Compose
     * catalog. Async because the resource read is suspending; CarPlay defers
     * building its first template until [completion] fires so immutable
     * template titles are never blank.
     */
    fun loadCarPlayStrings(completion: (CarPlayStrings) -> Unit) {
        mainScope.launch { completion(CarPlayStrings.load()) }
    }

    /** Whether the local player is enabled; CarPlay gates attachment on this. */
    fun isLocalPlayerEnabled(): Boolean = settingsRepository.sendspinEnabled.value

    fun onExternalConsumerActive() = serviceClient.onExternalConsumerActive()
    fun onExternalConsumerInactive() = serviceClient.onExternalConsumerInactive()
    fun refreshCarPlayNowPlayingState() = mainDataSource.refreshPlayersAndQueues()

    // MARK: - Artwork loader (Swift-callable)
    //
    // Swift CarPlay / MPNowPlayingInfoCenter previously fetched artwork through
    // `URLSession.shared.dataTask(...)`. That bypasses the WebRTC data-channel proxy
    // (URLSession can't speak `mawebrtc://`) and breaks lock-screen / CarPlay artwork
    // whenever the URL we hand Swift is a synthetic proxy URL. This helper routes:
    //   - `mawebrtc://...` → `WebRTCHttpProxy.get(...)` (returns hex-decoded bytes)
    //   - `http(s)://...`  → Ktor GET
    // Swift consumes the NSData and builds the UIImage.
    fun loadArtworkBytes(
        urlString: String,
        completion: (NSData?) -> Unit,
    ): Cancellable {
        val job = mainScope.launch {
            val bytes = try {
                fetchArtworkInternal(urlString)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.w { "loadArtworkBytes failed for $urlString: ${e.message}" }
                null
            }
            completion(bytes?.toNSData())
        }
        return Cancellable { job.cancel() }
    }

    private suspend fun fetchArtworkInternal(urlString: String): ByteArray? = when {
        urlString.startsWith("mawebrtc://") -> {
            val proxy = serviceClient.webRTCHttpProxy ?: return null
            val parsed = Url(urlString)
            val tail = parsed.encodedQuery.let { q ->
                if (q.isEmpty()) parsed.encodedPath else "${parsed.encodedPath}?$q"
            }
            val response = proxy.get(tail)
            response.body.takeIf { response.status in 200..299 }
        }
        urlString.startsWith("http://") || urlString.startsWith("https://") -> {
            val response = artworkHttpClient.get(urlString)
            response.readRawBytes().takeIf { response.status.value in 200..299 }
        }
        else -> null
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }

    // MARK: - Readiness observation

    /**
     * Subscribe to transport command-readiness. Fires with the current value
     * on subscribe and on every change.
     */
    fun observeReadiness(onChanged: (Boolean) -> Unit): Cancellable {
        val job = mainScope.launch {
            serviceClient.isReadyForCommands.collect { onChanged(it) }
        }
        return Cancellable { job.cancel() }
    }

    // MARK: - Now Playing channels
    //
    // Per-concern state for the system media UI (lock screen / Control Center /
    // CarPlay). Each observer replays the current value on subscribe — late
    // subscribers (CarPlay connecting mid-playback, foreground return) catch up
    // immediately. Callbacks arrive on the main thread; Swift needs no dispatch
    // hop. `null` means "nothing to present" (no current track).

    /**
     * Subscribe to track metadata changes (identity, titles, artwork URL,
     * duration, long-form flag).
     */
    fun observeNowPlayingTrack(onChanged: (NowPlayingTrack?) -> Unit): Cancellable {
        val job = mainScope.launch {
            mainDataSource.nowPlayingTrack.collect { onChanged(it) }
        }
        return Cancellable { job.cancel() }
    }

    /**
     * Subscribe to transport anchor changes (playing state, position anchor,
     * rate). The anchor timestamp is only meaningful on the Kotlin side;
     * Swift re-stamps arrival with its own clock.
     */
    fun observeNowPlayingTransport(onChanged: (NowPlayingTransport?) -> Unit): Cancellable {
        val job = mainScope.launch {
            mainDataSource.nowPlayingTransport.collect { onChanged(it) }
        }
        return Cancellable { job.cancel() }
    }

    /** Subscribe to queue-mode changes (shuffle, repeat, toggle availability). */
    fun observeNowPlayingModes(onChanged: (NowPlayingModes?) -> Unit): Cancellable {
        val job = mainScope.launch {
            mainDataSource.nowPlayingModes.collect { onChanged(it) }
        }
        return Cancellable { job.cancel() }
    }

    // MARK: - Swift Helpers for Data Fetching
    //
    // Every fetcher returns a nullable list. `null` means the round trip
    // exceeded FETCH_TIMEOUT_MS — Swift renders a disconnected affordance.
    // An empty list means the server answered with nothing.

    private inline fun <T> launchFetch(
        label: String,
        crossinline completion: (List<T>?) -> Unit,
        crossinline fetch: suspend () -> List<T>,
    ) {
        val startMs = currentTimeMillis()
        log.i { "fetch[$label] start" }
        mainScope.launch {
            val items: List<T>? = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetch() }
            val elapsed = currentTimeMillis() - startMs
            if (items == null) {
                log.i { "fetch[$label] timeout after ${elapsed}ms" }
            } else {
                log.i { "fetch[$label] returned ${items.size} items in ${elapsed}ms" }
            }
            completion(items)
        }
    }

    /** Stations of the optional `ai_radio` plugin. Empty until the user authors one. */
    fun fetchAiRadioStations(completion: (List<ServerAiRadioStation>?) -> Unit) {
        launchFetch("aiRadioStations", completion) {
            aiRadioRepository.stations().getOrNull() ?: emptyList()
        }
    }

    /**
     * Starts an AI Radio station on the local player, mirroring [dispatchLocal]'s target: a
     * CarPlay tap wants audio out of the head unit, never mirrored to a house player.
     *
     * Returns false when there is no local player to start it on, so Swift can say so instead
     * of pushing a Now Playing screen that never fills.
     */
    fun startAiRadioStation(stationId: String): Boolean {
        val playerId = mainDataSource.localPlayer.value?.player?.id ?: run {
            log.w { "AI Radio: no local player — ignoring station $stationId" }
            return false
        }
        mainScope.launch {
            aiRadioRepository.start(stationId, playerId)
                .onFailure { log.w(it) { "AI Radio: start failed for $stationId" } }
        }
        return true
    }

    fun fetchRecommendations(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("recommendations", completion) {
            mediaItemRepository.fetchRecommendationFolders().getOrNull()
                ?: emptyList()
        }
    }

    fun fetchRecommendationFolders(
        completion: (List<RecommendationFolder>?) -> Unit,
    ) {
        launchFetch("recommendationFolders", completion) {
            mediaItemRepository.fetchRecommendationFolders().getOrNull()
                ?: emptyList()
        }
    }

    fun fetchPlaylists(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("playlists", completion) {
            mediaItemRepository.fetchMediaItems(Request.Playlist.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchAlbums(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("albums", completion) {
            mediaItemRepository.fetchMediaItems(Request.Album.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchArtists(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("artists", completion) {
            mediaItemRepository.fetchMediaItems(Request.Artist.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchAudiobooks(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("audiobooks", completion) {
            mediaItemRepository.fetchMediaItems(Request.Audiobook.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchTracks(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("tracks", completion) {
            mediaItemRepository.fetchMediaItems(Request.Track.list()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchPodcasts(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("podcasts", completion) {
            mediaItemRepository.fetchMediaItems(Request.Podcast.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchRadioStations(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("radioStations", completion) {
            mediaItemRepository.fetchMediaItems(Request.RadioStation.listLibrary()).getOrNull()
                ?: emptyList()
        }
    }

    fun search(query: String, completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("search:$query", completion) {
            val result = mediaItemRepository.search(
                Request.Library.search(
                    query = query,
                    mediaTypes = listOf(
                        MediaType.ARTIST,
                        MediaType.ALBUM,
                        MediaType.TRACK,
                        MediaType.PLAYLIST,
                        MediaType.AUDIOBOOK,
                        MediaType.RADIO,
                    ),
                    limit = 10,
                    libraryOnly = false,
                ),
            ).getOrNull()
            buildList<AppMediaItem> {
                result ?: return@buildList
                addAll(result.artists)
                addAll(result.albums)
                addAll(result.tracks)
                addAll(result.playlists)
                addAll(result.podcasts)
                addAll(result.audiobooks)
                addAll(result.radios)
                addAll(result.genres)
            }
        }
    }

    // MARK: - Drilldown fetchers (same nullable-on-timeout contract)

    fun fetchAlbumsByArtist(
        artist: Artist,
        completion: (List<AppMediaItem>?) -> Unit,
    ) {
        launchFetch("albumsByArtist:${artist.itemId}", completion) {
            mediaItemRepository.fetchMediaItems(
                Request.Artist.getAlbums(
                    itemId = artist.itemId,
                    providerInstanceIdOrDomain = artist.provider,
                ),
            ).getOrNull()
                ?.filterIsInstance<Album>()
                ?: emptyList()
        }
    }

    fun fetchTracksByAlbum(
        album: Album,
        completion: (List<AppMediaItem>?) -> Unit,
    ) {
        launchFetch("tracksByAlbum:${album.itemId}", completion) {
            mediaItemRepository.fetchMediaItems(
                Request.Album.getTracks(
                    itemId = album.itemId,
                    providerInstanceIdOrDomain = album.provider,
                ),
            ).getOrNull()
                ?.filterIsInstance<Track>()
                ?: emptyList()
        }
    }

    fun fetchTracksByPlaylist(
        playlist: Playlist,
        completion: (List<AppMediaItem>?) -> Unit,
    ) {
        launchFetch("tracksByPlaylist:${playlist.itemId}", completion) {
            mediaItemRepository.fetchMediaItems(
                Request.Playlist.getTracks(
                    itemId = playlist.itemId,
                    providerInstanceIdOrDomain = playlist.provider,
                    forceRefresh = null,
                ),
            ).getOrNull()
                ?.filterIsInstance<Track>()
                ?: emptyList()
        }
    }

    fun fetchEpisodesByPodcast(
        podcast: Podcast,
        completion: (List<AppMediaItem>?) -> Unit,
    ) {
        launchFetch("episodesByPodcast:${podcast.itemId}", completion) {
            mediaItemRepository.fetchMediaItems(
                Request.Podcast.getEpisodes(
                    itemId = podcast.itemId,
                    providerInstanceIdOrDomain = podcast.provider,
                ),
            ).getOrNull()
                ?.filterIsInstance<PodcastEpisode>()
                ?.clientSorted(
                    SortConfig.defaultFor(SubItemContext.PODCAST_EPISODES),
                    SubItemContext.PODCAST_EPISODES,
                )
                ?: emptyList()
        }
    }

    // MARK: - Playback

    /**
     * Play, replace, or append [item] on the iOS local Sendspin player —
     * never the group it may be synced to. When the local player is currently
     * a sync-group child we always detach first, regardless of [option]:
     * being in CarPlay means the user wants audio out of the phone they're
     * holding, and there's no plausible scenario where they want the same
     * audio mirrored to another player as well.
     *
     * Returns false on no-local-player or no-URI; callers use this to skip
     * Siri donation and respond with `.failure`.
     */
    fun playOnLocalPlayer(item: AppMediaItem, option: QueueOption): Boolean =
        dispatchLocal(item, option, endlessMixMode = false)

    private fun dispatchLocal(item: AppMediaItem, option: QueueOption, endlessMixMode: Boolean): Boolean {
        return dispatchLocal(
            mediaUris = listOfNotNull(item.mediaUri),
            option = option,
            endlessMixMode = endlessMixMode,
        )
    }

    private fun dispatchLocal(
        mediaUris: List<String>,
        option: QueueOption,
        endlessMixMode: Boolean,
        startItem: String? = null,
    ): Boolean {
        val player = mainDataSource.localPlayer.value?.player
        val plan = planLocalPlayerDispatch(
            localPlayerId = player?.id,
            localPlayerSyncedTo = player?.syncedTo,
            mediaUris = mediaUris,
            option = option,
            endlessMixMode = endlessMixMode,
            startItem = startItem,
        ) ?: return false
        plan.detachFrom?.let { syncedToId ->
            log.i { "dispatchLocal($option, endlessMix=$endlessMixMode): detaching ${plan.playerId} from $syncedToId" }
        }
        mainScope.launch {
            executeLocalPlayerDispatch(serviceClient, plan) { label, error ->
                log.w(error) { "$label RPC failed: ${error.message}" }
            }
        }
        return true
    }

    // MARK: - Configurable Car actions (shared with Android Auto via SettingsRepository)

    /**
     * The ordered, CarPlay-supported bulk-action names (DefaultClickAction.name) configured for
     * [item]'s browsable kind. Empty when the item isn't a browsable container. Swift maps each
     * name to a localized title (CarPlayStrings.bulkActionTitle) and dispatches via [playCarAction].
     */
    fun carBulkActionNames(item: AppMediaItem): List<String> {
        val kind = item.mediaType.toItemKind() ?: return emptyList()
        return settingsRepository.carBrowsableBulkActions.value
            .carBulkActions(kind, CarPlatform.CARPLAY)
            .map { it.name }
    }

    /** Dispatch a named [DefaultClickOption] (a bulk button) onto [item]. False if invalid/no-op. */
    fun playCarAction(item: AppMediaItem, actionName: String): Boolean {
        val action = runCatching { DefaultClickOption.valueOf(actionName) }.getOrNull() ?: return false
        val dispatch = action.toCarDispatch()
        return dispatchLocal(item, dispatch.option, dispatch.endlessMixMode)
    }

    /**
     * Dispatch the per-kind tap action configured for [item] (a plain item tap). Returns the
     * dispatched DefaultClickAction.name so Swift can decide whether to push Now Playing, or null
     * on failure / no playable URI.
     */
    fun playCarDefaultTap(item: AppMediaItem, parent: AppMediaItem?): String? {
        val action = item.mediaType.toItemKind()
            ?.let { settingsRepository.carPlayableClickActions.value.carTapAction(it) }
            ?: DefaultClickOption.PLAY_NOW
        val dispatch = planCarItemDispatch(
            action = action,
            itemUri = item.mediaUri,
            itemId = item.itemId,
            parentUri = parent?.mediaUri,
        ) ?: return null
        return if (
            dispatchLocal(
                mediaUris = dispatch.mediaUris,
                option = dispatch.option,
                endlessMixMode = dispatch.endlessMixMode,
                startItem = dispatch.startItem,
            )
        ) {
            action.name
        } else {
            null
        }
    }

    /**
     * The ordered, enabled CarPlay browse-grid categories from the user's Car Tabs setting.
     * Returns LibraryCategory.name strings (e.g. "ARTISTS", "ALBUMS") so Swift can map each
     * to its fetcher and icon. Falls back to the car-supported set when no config is stored.
     * Tracks and Genres are excluded because they are not car tabs.
     *
     * AI_RADIO is dropped while its plugin is absent or the user lacks its scope, so the grid
     * never offers a category whose every tap would fail.
     */
    fun carBrowseCategories(): List<String> = visibleCategories(
        reconcileCarTabs(settingsRepository.carTabsConfig.value),
        mainDataSource.aiRadioAvailable.value,
    ).filter { (_, enabled) -> enabled }.map { (category, _) -> category.name }

    // MARK: - Library Actions (Siri)

    /**
     * Set the favorite flag on an [AppMediaItem]. Drives `INMediaAffinityIntent`
     * mapping from Swift: `like` ⇒ `favorite = true`, `dislike` ⇒ `favorite = false`.
     * MA only tracks favorites as a boolean — dislike removes an existing favorite
     * but cannot record an explicit "do not play this" signal. Adding requires a
     * URI; removal works by id+mediaType.
     *
     * Returns `true` synchronously when the request was dispatched, `false` when
     * we couldn't form a valid request (the only known failure mode today: an
     * add for an item with no URI). Network outcome is fire-and-forget — Swift
     * uses the synchronous return to avoid lying to Siri about success.
     */
    fun setFavorite(item: AppMediaItem, favorite: Boolean): Boolean {
        if (favorite) {
            // Prefer plain `uri`; fall back to `mediaUri`. The base class uses
            // `uri` directly; subclasses like Genre override `mediaUri` to
            // synthesize one when the server didn't supply it.
            val uri = item.uri ?: item.mediaUri ?: return false
            mainScope.launch {
                serviceClient.sendRequest(Request.Library.addFavorite(uri))
            }
        } else {
            mainScope.launch {
                serviceClient.sendRequest(
                    Request.Library.removeFavorite(item.itemId, item.mediaType),
                )
            }
        }
        return true
    }
}
