package io.music_assistant.client.di

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * KmpHelper - Bridge for accessing Koin dependencies from Swift
 */
object KmpHelper : KoinComponent {
    val mainDataSource: MainDataSource by inject()
    val serviceClient: ServiceClient by inject()
    val authManager: AuthenticationManager by inject()

    // Provide a scope for Swift to launch coroutines if needed
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getServerUrl(): String? {
        return serviceClient.serverBaseUrl.value
    }

    // MARK: - External Consumer Lifecycle (CarPlay)

    fun onExternalConsumerActive() = serviceClient.onExternalConsumerActive()
    fun onExternalConsumerInactive() = serviceClient.onExternalConsumerInactive()

    // MARK: - Swift Helpers for Data Fetching

    fun fetchRecommendations(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Library.recommendations())
            val serverItems = result.resultAs<List<ServerMediaItem>>() ?: emptyList()
            val appItems = serverItems.toAppMediaItemList()
            completion(appItems)
        }
    }

    fun fetchRecommendationFolders(completion: (List<AppMediaItem.RecommendationFolder>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Library.recommendations())
            val serverItems = result.resultAs<List<ServerMediaItem>>() ?: emptyList()
            val folders = serverItems.toAppMediaItemList().filterIsInstance<AppMediaItem.RecommendationFolder>()
            completion(folders)
        }
    }

    fun fetchPlaylists(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
             val result = serviceClient.sendRequest(Request.Playlist.listLibrary())
             val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
             completion(items)
        }
    }

    fun fetchAlbums(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Album.listLibrary())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun fetchArtists(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Artist.listLibrary())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun fetchAudiobooks(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Audiobook.listLibrary())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun fetchTracks(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Track.list())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun fetchPodcasts(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.Podcast.listLibrary())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun fetchRadioStations(completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(Request.RadioStation.listLibrary())
            val items = result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    fun search(query: String, completion: (List<AppMediaItem>) -> Unit) {
        mainScope.launch {
            val result = serviceClient.sendRequest(
                Request.Library.search(
                    query = query,
                    mediaTypes = listOf(
                        io.music_assistant.client.data.model.server.MediaType.ARTIST,
                        io.music_assistant.client.data.model.server.MediaType.ALBUM,
                        io.music_assistant.client.data.model.server.MediaType.TRACK,
                        io.music_assistant.client.data.model.server.MediaType.PLAYLIST,
                        io.music_assistant.client.data.model.server.MediaType.AUDIOBOOK,
                        io.music_assistant.client.data.model.server.MediaType.RADIO,
                    ),
                    limit = 10,
                    libraryOnly = false,
                ),
            )
            val searchResult = result.resultAs<io.music_assistant.client.data.model.server.SearchResult>()
            val items = searchResult?.toAppMediaItemList() ?: emptyList()
            completion(items)
        }
    }

    // MARK: - Playback

    fun playMediaItem(item: AppMediaItem) {
        // Use selected player from MainDataSource
        val playerId = mainDataSource.selectedPlayerIndex.value?.let { index ->
             (mainDataSource.playersData.value as? io.music_assistant.client.ui.compose.common.DataState.Data)?.data?.get(index)?.playerId
        } ?: return

        playItem(item, playerId, QueueOption.PLAY)
    }

    private fun playItem(item: AppMediaItem, queueOrPlayerId: String, option: QueueOption) {
        item.mediaUri?.let { mediaUri ->
            mainScope.launch {
                serviceClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueOrPlayerId,
                        option = option,
                        radioMode = false,
                    ),
                )
            }
        }
    }
}
