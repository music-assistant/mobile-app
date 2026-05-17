package io.music_assistant.client.di

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    private val mediaItemRepository: MediaItemRepository by inject()

    // Provide a scope for Swift to launch coroutines if needed
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getServerUrl(): String? {
        return serviceClient.serverBaseUrl.value
    }

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

    // MARK: - External Consumer Lifecycle (CarPlay)

    fun onExternalConsumerActive() = serviceClient.onExternalConsumerActive()
    fun onExternalConsumerInactive() = serviceClient.onExternalConsumerInactive()

    // MARK: - Readiness observation

    /**
     * Subscribe to transport command-readiness. Fires with the current value
     * on subscribe and on every change. Caller must `cancel()` on teardown.
     */
    fun observeReadiness(onChanged: (Boolean) -> Unit): Cancellable {
        val job = mainScope.launch {
            serviceClient.isReadyForCommands.collect { onChanged(it) }
        }
        return Cancellable { job.cancel() }
    }

    /**
     * Subscribe to "local player has a current track" transitions. Fires
     * with the current value on subscribe, then on every distinct change.
     */
    fun observeLocalPlayerPresence(onChanged: (Boolean) -> Unit): Cancellable {
        val job = mainScope.launch {
            mainDataSource.localPlayer
                .map { it?.queueInfo?.currentItem != null }
                .distinctUntilChanged()
                .collect { onChanged(it) }
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

    fun fetchRecommendations(completion: (List<AppMediaItem>?) -> Unit) {
        launchFetch("recommendations", completion) {
            mediaItemRepository.fetchMediaItems(Request.Library.recommendations()).getOrNull()
                ?: emptyList()
        }
    }

    fun fetchRecommendationFolders(
        completion: (List<RecommendationFolder>?) -> Unit,
    ) {
        launchFetch("recommendationFolders", completion) {
            mediaItemRepository.fetchMediaItems(Request.Library.recommendations()).getOrNull()
                ?.filterIsInstance<RecommendationFolder>()
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
                    inLibraryOnly = false,
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
                    inLibraryOnly = false,
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

    // MARK: - Playback

    /**
     * Play [item] on the iOS local Sendspin player only — never the group
     * it may be synced to. Detaches from any sync group first; MA promotes
     * `play(queueOrPlayerId = childId)` to the group queue otherwise.
     *
     * Returns false on no-local-player or no-URI; callers use this to skip
     * Siri donation and respond with `.failure`.
     */
    fun playOnLocalPlayer(item: AppMediaItem): Boolean {
        val localPlayerData = mainDataSource.localPlayer.value ?: return false
        val playerId = localPlayerData.player.id
        val mediaUri = item.mediaUri ?: return false
        val syncedToId = localPlayerData.player.syncedTo
        mainScope.launch {
            if (syncedToId != null) {
                log.i { "playOnLocalPlayer: detaching $playerId from $syncedToId" }
                serviceClient.sendRequest(
                    Request.Player.setGroupMembers(
                        playerId = syncedToId,
                        playersToAdd = null,
                        playersToRemove = listOf(playerId),
                    ),
                )
            }
            serviceClient.sendRequest(
                Request.Library.play(
                    media = listOf(mediaUri),
                    queueOrPlayerId = playerId,
                    option = QueueOption.PLAY,
                    radioMode = false,
                ),
            )
        }
        return true
    }

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
