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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeScreenViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val jobs = mutableListOf<Job>()

    val serverUrl =
        apiClient.sessionState.map { (it as? SessionState.Connected)?.serverInfo?.baseUrl }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
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
                                // Load recommendations when data connection is ready
                                if (_recommendationsState.value.recommendations is DataState.Loading) {
                                    loadRecommendations()
                                }
                                _playersState.update { PlayersState.Loading }
                                stopJobs()
                                jobs.add(watchPlayersData())
                                jobs.add(watchSelectedPlayerData())
                            }

                            is DataConnectionState.AwaitingAuth -> {
                                when (connState.authProcessState) {
                                    AuthProcessState.NotStarted,
                                    AuthProcessState.InProgress -> {
                                        _playersState.update { PlayersState.Loading }
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
                                _playersState.update { PlayersState.Loading }
                                stopJobs()
                            }
                        }
                    }

                    SessionState.Connecting -> {
                        _playersState.update { PlayersState.Loading }
                        stopJobs()
                    }

                    is SessionState.Disconnected -> {
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

    private fun loadRecommendations() {
        viewModelScope.launch {
            _recommendationsState.update { it.copy(recommendations = DataState.Loading()) }
            getList<AppMediaItem.RecommendationFolder>(Request.Library.recommendations())
                ?.let { items ->
                    _recommendationsState.update { it.copy(recommendations = DataState.Data(items)) }
                } ?: run {
                _recommendationsState.update { it.copy(recommendations = DataState.Error()) }
            }
        }
    }

    fun onPlayClick(item: AppMediaItem, option: QueueOption, radio: Boolean) {
        dataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            item.uri?.let { uri ->
                viewModelScope.launch {
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(uri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio
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
        dataSource.playersData.collect { playerData ->
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
                            dataSource.localPlayer.value?.playerId
                        )

                        is DataState.Stale -> PlayersState.Data(
                            playerData.data,  // Show stale data as normal data
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId
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

    fun refreshPlayers() = dataSource.refreshPlayersAndQueues()
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
            val localPlayerId: String? = null
        ) : PlayersState()
    }
}