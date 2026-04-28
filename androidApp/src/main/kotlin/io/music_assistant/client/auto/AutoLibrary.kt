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
        when (id) {
            MediaIds.ROOT -> {
                result.sendResult(
                    listOf(
                        rootTabItem("Artists", MediaIds.TAB_ARTISTS),
                        rootTabItem("Albums", MediaIds.TAB_ALBUMS),
                        rootTabItem("Playlists", MediaIds.TAB_PLAYLISTS),
                        rootTabItem("Podcasts", MediaIds.TAB_PODCASTS),
                        rootTabItem("Radio", MediaIds.TAB_RADIO),
                        rootTabItem("Audiobooks", MediaIds.TAB_AUDIOBOOKS),
                    ),
                )
            }

            MediaIds.TAB_ARTISTS -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.Artist.listLibrary()))
                }
            }

            MediaIds.TAB_ALBUMS -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.Album.listLibrary()))
                }
            }

            MediaIds.TAB_PLAYLISTS -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.Playlist.listLibrary()))
                }
            }

            MediaIds.TAB_PODCASTS -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.Podcast.listLibrary()))
                }
            }

            MediaIds.TAB_RADIO -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.RadioStation.listLibrary()))
                }
            }

            MediaIds.TAB_AUDIOBOOKS -> {
                result.detach()
                scope.launch {
                    if (!waitForCorrectState()) {
                        result.sendResult(null)
                    return@launch
                    }
                    result.sendResult(loadItems(Request.Audiobook.listLibrary()))
                }
            }

            else -> {
                val parts = id.split("__")
                if (parts.size != ITEM_ID_PART_COUNT) {
                    result.sendResult(null)
                    return
                }
                result.detach()
                val parentType = MediaType.valueOf(parts[2])
                val requestAndCategory = when (parentType) {
                    MediaType.ARTIST -> Request.Artist.getAlbums(parts[0], parts[3])
                    MediaType.ALBUM -> Request.Album.getTracks(parts[0], parts[3])
                    MediaType.PLAYLIST -> Request.Playlist.getTracks(parts[0], parts[3])
                    MediaType.PODCAST -> Request.Podcast.getEpisodes(parts[0], parts[3])
                    else -> {
                        result.sendResult(null)
                        return
                    }
                }
                scope.launch {
                    val list = loadItems(requestAndCategory)
                    result.sendResult(list?.let { actionsForItem(id) + it })
                }
            }
        }
    }

    private suspend fun waitForCorrectState(): Boolean =
        withTimeoutOrNull(WAIT_FOR_AUTHENTICATED_TIMEOUT_MS) {
            apiClient.sessionState
                .mapNotNull { it as? SessionState.Connected }
                .mapNotNull { it.dataConnectionState as? DataConnectionState.Authenticated }
                .first()
        } != null

    private suspend fun loadItems(request: Request): List<MediaItem>? =
        apiClient.sendRequest(request)
            .resultAs<List<ServerMediaItem>>()
            ?.toAppMediaItemList()
            ?.sortedBy { item -> item.favorite != true }
            ?.map {
                it.toAutoMediaItem(
                    baseUrl,
                    true,
                    defaultIconUri,
                )
            }

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
                    mediaTypes = listOf(MediaType.TRACK, MediaType.ARTIST, MediaType.ALBUM, MediaType.PLAYLIST),
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

    private companion object {
        const val WAIT_FOR_AUTHENTICATED_TIMEOUT_MS = 30_000L

        // Encoded media item IDs are `tab__type__provider__providerItemId` — exactly 4 parts.
        const val ITEM_ID_PART_COUNT = 4
    }
}

internal object MediaIds {
    const val ROOT = "auto_lib_root"
    const val TAB_ARTISTS = "auto_lib_artists"
    const val TAB_ALBUMS = "auto_lib_albums"
    const val TAB_PLAYLISTS = "auto_lib_playlists"
    const val TAB_PODCASTS = "auto_lib_podcasts"
    const val TAB_RADIO = "auto_lib_radio"
    const val TAB_AUDIOBOOKS = "auto_lib_audiobooks"
    const val QUEUE_OPTION_KEY = "auto_queue_option"
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
        .setTitle((if (favorite == true) "\u2665 " else "") + title)
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
