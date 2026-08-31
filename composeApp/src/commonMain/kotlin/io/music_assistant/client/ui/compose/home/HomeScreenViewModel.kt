package io.music_assistant.client.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.Shortcut
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ServerUser
import io.music_assistant.client.data.model.server.supportsLeaderLeave
import io.music_assistant.client.data.model.server.supportsSleepTimer
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

val HomeScreenViewModel.PlayersState.Data.selectedPlayer: PlayerData?
    get() = selectedPlayerIndex?.let(playerData::getOrNull)

@OptIn(FlowPreview::class)
class HomeScreenViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val settings: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val jobs = mutableListOf<Job>()
    private var loadDataJob: Job? = null

    private val _links = MutableSharedFlow<String>()
    val links = _links.asSharedFlow()

    // Local (Sendspin) player identity — used by the group dialog to decide
    // whether to show the playback-delay adjuster.
    val localPlayerId: String
        get() = settings.sendspinEffectivePlayerId.value

    fun adjustSendspinStaticDelayMs(deltaMs: Int) {
        settings.setSendspinStaticDelayMs(settings.sendspinStaticDelayMs.value + deltaMs)
    }

    /** Live elapsed-time flow for the slider. Ticks at 500 ms only while playing + subscribed. */
    fun observePosition(queueId: String) = dataSource.positionTracker.observe(queueId)

    /**
     * Server-synced `audiobook_chapter_progress` gate for the chapter-relative timeline.
     * The web frontend owns the toggle; this client refreshes it on connect.
     */
    val chapterProgressEnabled: StateFlow<Boolean> =
        dataSource.userPreferences.chapterProgressEnabled
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Seconds of audio buffered ahead of the local playhead, sampled to ~2 Hz so the buffered
     * segment on the slider tracks the position tick without spamming recomposition.
     */
    fun observeLocalBufferedSeconds() = dataSource.localBufferedSeconds.sample(BUFFER_REAL_INTERVAL)

    /** User toggle: whether the now-playing slider draws the buffered-ahead segment. */
    val showBufferVisualization = settings.showBufferVisualization

    /** Server-side sleep timers exist from schema 35 on; hide the whole feature below that. */
    val sleepTimerSupported: StateFlow<Boolean> =
        apiClient.sessionState
            .map { supportsSleepTimer((it as? HasConnectionData)?.serverInfo?.schemaVersion) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    /**
     * Older servers dissolve the group and stop playback when the leader leaves, so the
     * leave gesture is hidden below the handoff floor.
     */
    val leaderLeaveSupported: StateFlow<Boolean> =
        apiClient.sessionState
            .map { supportsLeaderLeave((it as? HasConnectionData)?.serverInfo?.schemaVersion) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _connectionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
    val connectionState = _connectionState.asStateFlow()

    private val _state = MutableStateFlow(
        State(
            shortcuts = DataState.Loading(),
            recommendations = DataState.Loading(),
            homeRowsConfig = settings.homeRowsConfig.value,
        ),
    )
    val state = _state.asStateFlow()

    private val _playersState =
        MutableStateFlow<PlayersState>(PlayersState.Loading)
    val playersState = _playersState.asStateFlow()

    init {
        viewModelScope.launch {
            apiClient.sessionState.collect { connection ->
                _connectionState.value = connection
                when (connection) {
                    is SessionState.Reconnecting -> {
                        // Preserve UI state during reconnection - don't stop jobs or reload data
                        // UI stays in current state (e.g., showing players, recommendations)
                    }

                    is SessionState.Connected -> {
                        when (val connState = connection.dataConnectionState) {
                            is DataConnectionState.Authenticated -> {
                                if (_state.value.recommendations !is DataState.Data) {
                                    loadData()
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
                                    AuthProcessState.InProgress,
                                        -> {
                                        if (_playersState.value !is PlayersState.Data) {
                                            _playersState.update { PlayersState.Loading }
                                        }
                                        stopJobs()
                                    }

                                    AuthProcessState.LoggedOut,
                                    is AuthProcessState.Failed,
                                        -> {
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
                        loadDataJob?.cancel()
                        stopJobs()
                    }

                    is SessionState.Disconnected -> {
                        loadDataJob?.cancel()
                        when (connection) {
                            is SessionState.Disconnected.Error,
                            SessionState.Disconnected.Initial,
                            SessionState.Disconnected.ByUser,
                                -> {
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

        viewModelScope.launch {
            settings.homeRowsConfig.collect { config ->
                _state.update { it.copy(homeRowsConfig = config) }
            }
        }

        // Listen to real-time library changes to refresh tracks already shown
        // in the recommendations grid.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                (change.item as? Track)?.let { updateRecommendationsIfNeeded(it) }
            }
        }
    }

    fun loadData() {
        loadDataJob?.cancel()

        _state.update {
            it.copy(
                recommendations = DataState.Loading(),
                shortcuts = DataState.Loading(),
            )
        }

        loadDataJob = viewModelScope.launch {
            launch { loadShortcuts() }
            loadRecommendations()
        }
    }

    private suspend fun loadRecommendations() {
        val folders = mediaItemRepository.fetchRecommendationRows().getOrElse { error ->
            if (error is CancellationException) throw error
            Logger.e("Error fetching recommendations: $error")
            _state.update { it.copy(recommendations = DataState.Error()) }
            return
        }

        if (!mediaItemRepository.supportsRecommendationRowItems()) {
            setRecommendationRows(
                folders.map { RecommendationRowState(it, DataState.Data(it.items.orEmpty())) },
            )
            return
        }

        // Show every row as a loading placeholder, then fetch each row's items
        // as its own job.
        setRecommendationRows(folders.map { RecommendationRowState(it, DataState.Loading()) })
        coroutineScope {
            folders.forEach { folder ->
                launch {
                    setRowItems(
                        folder,
                        mediaItemRepository.fetchRecommendationRowItems(folder).orEmpty(),
                    )
                }
            }
        }
    }

    private fun setRecommendationRows(rows: List<RecommendationRowState>) {
        _state.update { it.copy(recommendations = DataState.Data(rows)) }
    }

    private fun setRowItems(folder: RecommendationFolder, items: List<AppMediaItem>) {
        _state.update { state ->
            val rows = (state.recommendations as? DataState.Data)?.data
                ?: return@update state
            val updated = rows.map { row ->
                if (row.folder.itemId == folder.itemId && row.folder.provider == folder.provider) {
                    row.copy(items = DataState.Data(items))
                } else {
                    row
                }
            }
            state.copy(recommendations = DataState.Data(updated))
        }
    }

    private suspend fun loadShortcuts() {
        val shortcutUris = apiClient.sendRequest(Request(APICommands.AUTH_ME))
            .resultAs<ServerUser>()?.preferences?.shortcuts
        val shortcuts = shortcutUris?.mapNotNull {
            mediaItemRepository.fetchMediaItem(
                Request(
                    command = APICommands.MUSIC_ITEM_BY_URI,
                    args = buildJsonObject {
                        put("uri", JsonPrimitive(it))
                    },
                ),
            ).getOrNull()
        }?.map { Shortcut(it) }
        _state.update {
            it.copy(
                shortcuts = if (shortcuts != null) DataState.Data(shortcuts) else DataState.NoData(),
            )
        }
    }

    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        dataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            item.mediaUri?.let { mediaUri ->
                viewModelScope.launch {
                    Logger.withTag("PlayDispatch")
                        .i { "HomeScreenViewModel: uri=$mediaUri option=$option radio=$radio queue=$queueId" }
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(mediaUri),
                            queueOrPlayerId = queueId,
                            option = option,
                            endlessMixMode = radio && item !is Genre,
                        ),
                    )
                }
            }
        }
    }

    private fun updateRecommendationsIfNeeded(changed: Track) {
        // Read-and-map inside the update lambda so a concurrent recommendations
        // write can never be clobbered with rows derived from a stale read.
        _state.update { state ->
            val rows = (state.recommendations as? DataState.Data)?.data
                ?: return@update state
            val updated = rows.map { row ->
                val items = (row.items as? DataState.Data)?.data ?: return@map row
                val updatedItems = items.map { item ->
                    if (item is Track && item.hasAnyMappingFrom(changed)) changed else item
                }
                row.copy(items = DataState.Data(updatedItems))
            }
            state.copy(recommendations = DataState.Data(updated))
        }
    }

    private fun stopJobs() {
        jobs.forEach { job -> job.cancel() }
        jobs.clear()
    }

    private fun watchPlayersData(): Job = viewModelScope.launch {
        combine(
            dataSource.playersData,
            dataSource.sendspinState,
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
                            sendspinState,
                        )

                        is DataState.Stale -> PlayersState.Data(
                            playerData.data,  // Show stale data as normal data
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId,
                            sendspinState,
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
    fun setSleepTimer(playerId: String, seconds: Int) =
        dataSource.setSleepTimer(playerId, seconds)

    fun clearSleepTimer(playerId: String) = dataSource.clearSleepTimer(playerId)

    fun onPlayersSortChanged(newSort: List<String>) = dataSource.onPlayersSortChanged(newSort)
    fun openPlayerSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken().orEmpty()}#/settings/editplayer/$id")
    }

    fun openPlayerDspSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken().orEmpty()}#/settings/editplayer/$id/dsp")
    }

    private fun currentServerToken(): String? =
        (apiClient.sessionState.value as? SessionState.Connected)
            ?.serverInfo?.serverId?.let { settings.getTokenForServer(it) }

    private fun onOpenExternalLink(url: String) = viewModelScope.launch { _links.emit(url) }

    /**
     * Persists the edited working list. Prefs for folders not currently present
     * on the server (e.g. temporarily item-less, so absent from the working list)
     * are carried over so their visibility/order isn't lost.
     */
    fun saveHomeRows(working: List<SettingsRepository.HomeRowPref>) {
        val presentIds = working.mapTo(mutableSetOf()) { it.id }
        val carriedOver = settings.homeRowsConfig.value.filterNot { it.id in presentIds }
        settings.setHomeRowsConfig(working + carriedOver)
    }

    data class State(
        val shortcuts: DataState<List<Shortcut>>,
        val recommendations: DataState<List<RecommendationRowState>>,
        val homeRowsConfig: List<SettingsRepository.HomeRowPref> = emptyList(),
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
            val sendspinState: SendspinState? = null,
        ) : PlayersState()
    }

    private companion object {
        private const val BUFFER_REAL_INTERVAL = 500L
    }
}

/**
 * One home-page recommendation row: the folder identity plus its items as an
 * independently loading [DataState], so each row can render a placeholder
 * while its contents are fetched.
 */
data class RecommendationRowState(
    val folder: RecommendationFolder,
    val items: DataState<List<AppMediaItem>>,
) {
    /** The row's items when resolved, or null while still loading (or on error). */
    val resolvedItems: List<AppMediaItem>? get() = (items as? DataState.Data)?.data
}
