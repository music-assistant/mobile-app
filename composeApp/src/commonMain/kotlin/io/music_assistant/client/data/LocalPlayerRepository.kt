package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.server.PlayerType
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class LocalPlayerRepository(
    private val settings: SettingsRepository,
    private val apiClient: ServiceClient,
    private val mediaPlayerController: MediaPlayerController,
) : CoroutineScope {
    private val log = Logger.withTag("LocalPlayerRepo")

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val _localPlayerData = MutableStateFlow<PlayerData?>(null)
    val localPlayerData: StateFlow<PlayerData?> = _localPlayerData.asStateFlow()

    private val commandQueueMutex = Mutex()
    private val commandQueue = mutableListOf<QueuedEntry>()

    private data class QueuedEntry(val action: PlayerAction, val request: Request)

    // --- Optimistic UI updates (called by MainDataSource before sending command) ---

    fun applyOptimisticUpdate(data: PlayerData, action: PlayerAction) {
        when (action) {
            PlayerAction.TogglePlayPause -> {
                val wasPlaying = data.player.isPlaying
                if (wasPlaying) mediaPlayerController.pauseSink() else mediaPlayerController.resumeSink()
                _localPlayerData.update { current ->
                    current?.copy(
                        player = current.player.copy(isPlaying = !wasPlaying),
                        pendingPlay = !wasPlaying,
                    )
                }
            }

            PlayerAction.Play -> {
                mediaPlayerController.resumeSink()
                _localPlayerData.update { current ->
                    current?.copy(
                        player = current.player.copy(isPlaying = true),
                        pendingPlay = true,
                    )
                }
            }

            PlayerAction.Pause -> {
                mediaPlayerController.pauseSink()
                _localPlayerData.update { current ->
                    current?.copy(
                        player = current.player.copy(isPlaying = false),
                        pendingPlay = false,
                    )
                }
            }

            is PlayerAction.ToggleShuffle -> {
                updateOptimisticQueueInfo { it.copy(shuffleEnabled = !action.current) }
            }

            is PlayerAction.ToggleRepeatMode -> {
                val nextMode = when (action.current) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
                updateOptimisticQueueInfo { it.copy(repeatMode = nextMode) }
            }

            // SeekTo: position tracker in MainDataSource handles immediate feedback
            // Next/Previous: no optimistic UI change (we don't know the next track)
            else -> {}
        }
    }

    // --- Command queue (online: send immediately, offline: queue with dedup) ---

    suspend fun sendOrQueue(action: PlayerAction, request: Request) {
        if (apiClient.isReadyForCommands.value) {
            apiClient.sendRequest(request)
        } else {
            enqueue(action, request)
        }
    }

    fun drainCommandQueue() {
        launch {
            val entries = commandQueueMutex.withLock {
                if (commandQueue.isEmpty()) return@launch
                log.i { "Draining ${commandQueue.size} queued commands" }
                commandQueue.toList().also { commandQueue.clear() }
            }
            entries.forEach { entry ->
                apiClient.sendRequest(entry.request)
                delay(100)
            }
        }
    }

    // --- Server event reconciliation ---

    fun onServerPlayerUpdate(player: Player) {
        _localPlayerData.update { current ->
            current?.copy(
                player = player,
                pendingPlay = if (player.isPlaying) false else current.pendingPlay,
            ) ?: run {
                if (!settings.sendspinEnabled.value) return@update null
                PlayerData(
                    player = player,
                    queue = DataState.NoData(),
                    parentBind = null,
                    childrenBinds = emptyList(),
                    isLocal = true,
                )
            }
        }
    }

    fun onServerQueueUpdate(queueInfo: QueueInfo) {
        _localPlayerData.update { current ->
            current?.copy(
                queue = DataState.Data(
                    Queue(
                        info = queueInfo,
                        items = (current.queue as? DataState.Data)?.data?.items
                            ?: DataState.NoData(),
                    ),
                ),
            )
        }
    }

    fun onQueueItemsLoaded(queueInfo: QueueInfo, items: List<QueueTrack>) {
        _localPlayerData.update { current ->
            current?.copy(
                queue = DataState.Data(Queue(info = queueInfo, items = DataState.Data(items))),
            )
        }
    }

    // --- Synthetic player ---

    fun onInitialPlayersReceived(hasLocalPlayer: Boolean) {
        if (!settings.sendspinEnabled.value) {
            _localPlayerData.update { null }
            return
        }
        if (!hasLocalPlayer && _localPlayerData.value == null) {
            log.i { "Injecting synthetic local player" }
            _localPlayerData.update {
                PlayerData(
                    player = Player(
                        id = settings.sendspinClientId.value,
                        name = settings.sendspinDeviceName.value,
                        provider = "builtin",
                        type = PlayerType.PLAYER,
                        shouldBeShown = true,
                        canSetVolume = false,
                        volumeLevel = null,
                        volumeControl = null,
                        volumeMuted = false,
                        canMute = false,
                        queueId = null,
                        isPlaying = false,
                        isAnnouncing = false,
                        canGroupWith = null,
                        groupMembers = null,
                        staticGroupMembers = null,
                        groupVolume = null,
                        activeGroup = null,
                        syncedTo = null,
                    ),
                    queue = DataState.NoData(),
                    parentBind = null,
                    childrenBinds = emptyList(),
                    isLocal = true,
                )
            }
        }
    }

    // --- Lifecycle ---

    fun clearState() {
        _localPlayerData.update { null }
        launch { commandQueueMutex.withLock { commandQueue.clear() } }
    }

    // --- Private helpers ---

    private fun updateOptimisticQueueInfo(transform: (QueueInfo) -> QueueInfo) {
        _localPlayerData.update { current ->
            current?.let { pd ->
                val queueData = pd.queue as? DataState.Data ?: return@update pd
                pd.copy(
                    queue = DataState.Data(
                        queueData.data.copy(info = transform(queueData.data.info)),
                    ),
                )
            }
        }
    }

    private suspend fun enqueue(action: PlayerAction, request: Request) {
        commandQueueMutex.withLock {
            val entry = QueuedEntry(action, request)
            when (action) {
                PlayerAction.TogglePlayPause -> {
                    val idx = commandQueue.indexOfFirst { it.action is PlayerAction.TogglePlayPause }
                    if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
                }

                PlayerAction.Play -> {
                    commandQueue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    commandQueue.add(entry)
                }

                PlayerAction.Pause -> {
                    commandQueue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    commandQueue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> {
                    val idx = commandQueue.indexOfFirst { it.action is PlayerAction.ToggleShuffle }
                    if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
                }

                is PlayerAction.ToggleRepeatMode -> {
                    commandQueue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    commandQueue.add(entry)
                }

                is PlayerAction.SeekTo -> {
                    Logger.e("SeekTo: ${action.position}")
                    commandQueue.removeAll { it.action is PlayerAction.SeekTo }
                    commandQueue.add(entry)
                }

                else -> commandQueue.add(entry)
            }
        }
    }
}
