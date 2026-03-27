package io.music_assistant.client.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeScreenViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val jobs = mutableListOf<Job>()
    private var recommendationsJob: Job? = null

    val serverUrl = apiClient.serverBaseUrl
    private val _links = MutableSharedFlow<String>()
    val links = _links.asSharedFlow()


    private val _recommendationsState = MutableStateFlow(
        RecommendationsState(
            connectionState = SessionState.Disconnected.Initial,
            recommendations = DataState.Loading()
        )
    )
    val recommendationsState = _recommendationsState.asStateFlow()

    private val _playersState =
        MutableStateFlow<PlayersState>(PlayersState.Loading)
    val playersState = _playersState.asStateFlow()

    init {
        viewModelScope.launch {
            apiClient.sessionState.collect { connection ->
                _recommendationsState.update { state -> state.copy(connectionState = connection) }
                when (connection) {
                    is SessionState.Reconnecting -> {
                        // Preserve UI state during reconnection - don't stop jobs or reload data
                        // UI stays in current state (e.g., showing players, recommendations)
                    }

                    is SessionState.Connected -> {
                        when (val connState = connection.dataConnectionState) {
                            DataConnectionState.Authenticated -> {
                                if (_recommendationsState.value.recommendations !is DataState.Data) {
                                    recommendationsJob?.cancel()
                                    recommendationsJob = loadRecommendations()
                                }
                                // Only show loading if we don't have cached data (e.g. fresh connect).
                                // During reconnection the existing player list stays visible.
                                if (_playersState.value !is PlayersState.Data) {
                                    _playersState.update { PlayersState.Loading }
                                }
                                stopJobs()
                                jobs.add(watchPlayersData())
                                jobs.add(watchSelectedPlayerData())
                            }

                            is DataConnectionState.AwaitingAuth -> {
                                when (connState.authProcessState) {
                                    AuthProcessState.NotStarted,
                                    AuthProcessState.InProgress -> {
                                        if (_playersState.value !is PlayersState.Data) {
                                            _playersState.update { PlayersState.Loading }
                                        }
                                        stopJobs()
                                    }

                                    AuthProcessState.LoggedOut,
                                    is AuthProcessState.Failed -> {
                                        _playersState.update { PlayersState.NoAuth }
                                        stopJobs()
                                    }
                                }
                            }

                            DataConnectionState.AwaitingServerInfo -> {
                                if (_playersState.value !is PlayersState.Data) {
                                    _playersState.update { PlayersState.Loading }
                                }
                                stopJobs()
                            }
                        }
                    }

                    SessionState.Connecting -> {
                        if (_playersState.value !is PlayersState.Data) {
                            _playersState.update { PlayersState.Loading }
                        }
                        recommendationsJob?.cancel()
                        stopJobs()
                    }

                    is SessionState.Disconnected -> {
                        recommendationsJob?.cancel()
                        when (connection) {
                            is SessionState.Disconnected.Error,
                            SessionState.Disconnected.Initial,
                            SessionState.Disconnected.ByUser -> {
                                _playersState.update { PlayersState.Disconnected }
                                stopJobs()
                            }

                            SessionState.Disconnected.NoServerData -> {
                                _playersState.update { PlayersState.NoServer }
                                stopJobs()
                            }

                            SessionState.Disconnected.Backgrounded -> {
                                // Preserve current state for instant foreground reconnect
                            }
                        }

                    }
                }
            }
        }

        // Listen to real-time events for track updates in recommendations
        viewModelScope.launch {
            apiClient.events.collect { event ->
                when (event) {
                    is MediaItemUpdatedEvent,
                    is MediaItemAddedEvent,
                    is MediaItemDeletedEvent -> {
                        event.data?.let { updateRecommendationsIfNeeded(it) }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun loadRecommendations(): Job = viewModelScope.launch {
        _recommendationsState.update { it.copy(recommendations = DataState.Loading()) }
        repeat(3) { attempt ->
            if (attempt > 0) delay(2_000L)
            getList<AppMediaItem.RecommendationFolder>(Request.Library.recommendations())
                ?.let { items ->
                    _recommendationsState.update { it.copy(recommendations = DataState.Data(items)) }
                    return@launch
                }
        }
        _recommendationsState.update { it.copy(recommendations = DataState.Error()) }
    }

    fun onPlayClick(item: AppMediaItem, option: QueueOption, radio: Boolean) {
        dataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            item.mediaUri?.let { mediaUri ->
                viewModelScope.launch {
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(mediaUri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio && item !is AppMediaItem.Genre
                        )
                    )
                }
            }
        }
    }

    private fun updateRecommendationsIfNeeded(serverItem: ServerMediaItem) {
        val recommendationsData =
            (_recommendationsState.value.recommendations as? DataState.Data)?.data
        if (recommendationsData != null) {
            val updated = recommendationsData.map { row ->
                row.items?.let { itemsList ->
                    val updatedItems = itemsList.map { item ->
                        if (item is AppMediaItem.Track && item.hasAnyMappingFrom(serverItem)) {
                            serverItem.toAppMediaItem() as? AppMediaItem.Track ?: item
                        } else {
                            item
                        }
                    }
                    // Create new RecommendationFolder with updated items
                    AppMediaItem.RecommendationFolder(
                        itemId = row.itemId,
                        provider = row.provider,
                        name = row.name,
                        providerMappings = row.providerMappings,
                        uri = row.uri,
                        image = row.image,
                        items = updatedItems
                    )
                } ?: row
            }
            _recommendationsState.update {
                it.copy(recommendations = DataState.Data(updated))
            }
        }
    }

    private fun stopJobs() {
        jobs.forEach { job -> job.cancel() }
        jobs.clear()
    }

    private fun watchPlayersData(): Job = viewModelScope.launch {
        combine(
            dataSource.playersData,
            dataSource.sendspinState
        ) { playerData, sendspinState ->
            playerData to sendspinState
        }.collect { (playerData, sendspinState) ->
            // Update when in Loading or Data state
            // This allows transitioning from Loading to Data and updating existing Data
            // Don't update terminal states (Disconnected, NoAuth, NoServer)
            val currentState = _playersState.value
            if (currentState is PlayersState.Loading || currentState is PlayersState.Data) {
                _playersState.update {
                    when (playerData) {
                        is DataState.Data -> PlayersState.Data(
                            playerData.data,
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId,
                            sendspinState
                        )

                        is DataState.Stale -> PlayersState.Data(
                            playerData.data,  // Show stale data as normal data
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId,
                            sendspinState
                        )

                        is DataState.Error -> PlayersState.Error
                        is DataState.Loading -> PlayersState.Loading
                        is DataState.NoData -> PlayersState.Data(emptyList())
                    }

                }
            }
        }
    }

    private fun watchSelectedPlayerData(): Job = viewModelScope.launch {
        dataSource.selectedPlayerIndex.filterNotNull().collect { index ->
            val dataState = _playersState.value as? PlayersState.Data
            dataState?.let { state ->
                _playersState.update { state.copy(selectedPlayerIndex = index) }
            }
        }
    }

    fun selectPlayer(player: Player) = dataSource.selectPlayer(player)
    fun playerAction(playerId: String, action: PlayerAction) =
        dataSource.playerAction(playerId, action)

    fun playerAction(data: PlayerData, action: PlayerAction) = dataSource.playerAction(data, action)
    fun queueAction(action: QueueAction) = dataSource.queueAction(action)
    fun onPlayersSortChanged(newSort: List<String>) = dataSource.onPlayersSortChanged(newSort)
    fun openPlayerSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken() ?: ""}#/settings/editplayer/$id")
    }

    fun openPlayerDspSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken() ?: ""}#/settings/editplayer/$id/dsp")
    }

    private fun currentServerToken(): String? = when (val state = apiClient.sessionState.value) {
        is SessionState.Connected.Direct ->
            settings.getTokenForServer(settings.getDirectServerIdentifier(state.connectionInfo.host, state.connectionInfo.port, state.connectionInfo.isTls))
        is SessionState.Connected.WebRTC ->
            settings.getTokenForServer(settings.getWebRTCServerIdentifier(state.remoteId.rawId))
        else -> null
    }

    private fun onOpenExternalLink(url: String) = viewModelScope.launch { _links.emit(url) }


    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : AppMediaItem> getList(
        request: Request,
    ): List<T>? =
        apiClient.sendRequest(request).let { result ->
            if (result.isFailure) {
                Logger.e("Error fetching list for request $request: ${result.exceptionOrNull()}")
            }
            result.resultAs<List<ServerMediaItem>>()?.toAppMediaItemList()?.mapNotNull { it as? T }
        }

    data class RecommendationsState(
        val connectionState: SessionState,
        val recommendations: DataState<List<AppMediaItem.RecommendationFolder>>
    )

    sealed class PlayersState {
        data object Loading : PlayersState()
        data object Disconnected : PlayersState()
        data object NoServer : PlayersState()
        data object NoAuth : PlayersState()
        data object Error : PlayersState()
        data class Data(
            val playerData: List<PlayerData>,
            val selectedPlayerIndex: Int? = null,
            val localPlayerId: String? = null,
            val sendspinState: SendspinState? = null
        ) : PlayersState()
    }
}