package io.music_assistant.client.watch.data

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The one thing the watch remembers between launches: which player it controls. */
internal interface WatchPreferences {
    var selectedPlayerId: String?
}

/**
 * The watch's single source of truth: players, their queues, which player is selected, and
 * the queue rows of that player.
 *
 * Snapshots come from `players/all` and `player_queues/all` on every completed handshake, and
 * stay current through the server's event stream. Commands are sent optimistically — the local
 * model is updated at once and the next server event for that player overwrites it wholesale,
 * so a refused command corrects itself without extra bookkeeping.
 */
@OptIn(FlowPreview::class)
internal class MaRepository(
    private val session: MaSession,
    private val config: WatchServerConfig,
    private val preferences: WatchPreferences,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("WatchRepository")

    val sessionState: StateFlow<SessionState> = session.state

    private val _players = MutableStateFlow<List<WatchPlayer>>(emptyList())
    val players: StateFlow<List<WatchPlayer>> = _players.asStateFlow()

    private val _queues = MutableStateFlow<Map<String, WatchQueue>>(emptyMap())

    private val _selectedPlayerId = MutableStateFlow(preferences.selectedPlayerId)

    private val _queueItems = MutableStateFlow(WatchQueueState())
    val queueItems: StateFlow<WatchQueueState> = _queueItems.asStateFlow()

    private val queueReloads = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** `schema_version` of the connected server; decides which artwork endpoint to build. */
    private var schemaVersion: Int? = null

    val selectedPlayer: StateFlow<WatchPlayer?> =
        combine(_players, _selectedPlayerId) { players, id -> players.firstOrNull { it.id == id } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** The queue row that is playing now, so the queue screen can mark it. */
    val activeQueueItemId: StateFlow<String?> =
        combine(selectedPlayer, _queues) { player, queues ->
            player?.queueId?.let { queues[it] }?.activeItemId
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val nowPlaying: StateFlow<WatchNowPlaying?> =
        combine(selectedPlayer, _queues) { player, queues -> player?.let { nowPlaying(it, queues) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            session.sessionReady.collect { info ->
                schemaVersion = info.schemaVersion
                loadSnapshots()
            }
        }
        scope.launch { session.events.collect(::onEvent) }
        scope.launch {
            selectedPlayer
                .map { it?.queueId }
                .distinctUntilChanged()
                .collect { queueId ->
                    _queueItems.value =
                        WatchQueueState(queueId = queueId, isLoading = queueId != null)
                    queueId?.let { queueReloads.emit(it) }
                }
        }
        scope.launch {
            // `queue_items_updated` arrives in bursts while the server fills a queue; one
            // reload after the burst is enough and keeps the watch off the radio.
            queueReloads.debounce(QUEUE_RELOAD_DEBOUNCE_MS).collect(::loadQueueItems)
        }
    }

    fun selectPlayer(playerId: String) {
        _selectedPlayerId.value = playerId
        preferences.selectedPlayerId = playerId
    }

    fun togglePlayPause() {
        val player = selectedPlayer.value ?: return
        updatePlayer(player.id) { it.copy(isPlaying = !it.isPlaying) }
        sendPlayerCommand(player.id, ApiCommands.CMD_PLAY_PAUSE)
    }

    fun next() {
        val player = selectedPlayer.value ?: return
        sendPlayerCommand(player.id, ApiCommands.CMD_NEXT)
    }

    fun previous() {
        val player = selectedPlayer.value ?: return
        sendPlayerCommand(player.id, ApiCommands.CMD_PREVIOUS)
    }

    fun setVolume(target: Int) {
        val player = selectedPlayer.value ?: return
        setVolume(player, target.coerceIn(0, MAX_VOLUME))
    }

    /** Plays the queue row [itemId], which the server addresses by its `queue_item_id`. */
    fun playQueueItem(itemId: String) {
        val queueId = _queueItems.value.queueId ?: return
        request(
            ApiCommands.PLAYER_QUEUES_PLAY_INDEX,
            buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("index", JsonPrimitive(itemId))
            },
        )
    }

    /**
     * Removes the queue row [itemId]. The row disappears at once; the server's
     * `queue_items_updated` then reloads the window and settles any disagreement.
     */
    fun removeQueueItem(itemId: String) {
        val queueId = _queueItems.value.queueId ?: return
        _queueItems.update { state ->
            state.copy(items = state.items.filterNot { it.id == itemId })
        }
        request(
            ApiCommands.PLAYER_QUEUES_DELETE_ITEM,
            buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("item_id_or_index", JsonPrimitive(itemId))
            },
        )
    }

    /**
     * Hands what the selected player is playing to [targetPlayerId], and follows it there.
     *
     * The server moves the queue, so there is nothing to reconcile locally; selecting the
     * target is what keeps the next volume turn and the next tap pointed at the sound. A
     * paused player stays paused on arrival, which is what the phone app does too.
     */
    fun transferPlayback(targetPlayerId: String) {
        val source = selectedPlayer.value?.takeIf { it.id != targetPlayerId } ?: return
        request(
            ApiCommands.PLAYER_QUEUES_TRANSFER,
            buildJsonObject {
                put("source_queue_id", JsonPrimitive(source.queueId ?: source.id))
                put("target_queue_id", JsonPrimitive(targetPlayerId))
                put("auto_play", JsonPrimitive(source.isPlaying))
            },
        )
        selectPlayer(targetPlayerId)
    }

    private fun setVolume(player: WatchPlayer, level: Int) {
        updatePlayer(player.id) { it.copy(volume = level) }
        val command = if (player.usesGroupVolume) {
            ApiCommands.PLAYERS_CMD_GROUP_VOLUME
        } else {
            ApiCommands.PLAYERS_CMD_VOLUME_SET
        }
        request(
            command,
            buildJsonObject {
                put("player_id", JsonPrimitive(player.id))
                put("volume_level", JsonPrimitive(level))
            },
        )
    }

    private fun sendPlayerCommand(playerId: String, command: String) {
        request(
            ApiCommands.playersCmd(command),
            buildJsonObject { put("player_id", JsonPrimitive(playerId)) },
        )
    }

    private fun request(command: String, args: JsonObject?) {
        scope.launch {
            session.request(command, args).onFailure { logger.w { "$command: ${it.message}" } }
        }
    }

    private suspend fun loadSnapshots() {
        session.request(ApiCommands.PLAYERS_ALL)
            .decodeList(ServerPlayerDto.serializer())
            ?.let { dtos -> _players.value = dtos.map(::toWatchPlayer).also(::ensureSelection) }
        session.request(ApiCommands.PLAYER_QUEUES_ALL)
            .decodeList(ServerQueueDto.serializer())
            ?.let { dtos -> _queues.value = dtos.associate { it.queueId to toWatchQueue(it) } }
        selectedPlayer.value?.queueId?.let { queueReloads.emit(it) }
    }

    private suspend fun loadQueueItems(queueId: String) {
        // The watch shows a window around the current track instead of the whole queue: a
        // 500-item page is a large message for a device on a Bluetooth-proxied link.
        val index = _queues.value[queueId]?.currentIndex ?: 0
        val offset = (index - QUEUE_CONTEXT_BEFORE).coerceAtLeast(0)
        _queueItems.update { it.copy(queueId = queueId, isLoading = true) }
        val items = session.request(
            ApiCommands.PLAYER_QUEUES_ITEMS,
            buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("limit", JsonPrimitive(QUEUE_WINDOW))
                put("offset", JsonPrimitive(offset))
            },
        ).decodeList(ServerQueueItemDto.serializer())
        // A failed load keeps whatever the screen already shows.
        _queueItems.update { state ->
            if (state.queueId != queueId) {
                state
            } else {
                state.copy(isLoading = false, items = items?.map(::toWatchQueueItem) ?: state.items)
            }
        }
    }

    private suspend fun onEvent(message: JsonObject) {
        val type = message["event"]?.jsonPrimitive?.contentOrNull ?: return
        val data = message["data"]
        when (type) {
            EventTypes.PLAYER_ADDED, EventTypes.PLAYER_UPDATED ->
                data.decode(ServerPlayerDto.serializer())?.let { upsertPlayer(toWatchPlayer(it)) }

            EventTypes.PLAYER_REMOVED ->
                data?.jsonPrimitive?.contentOrNull?.let { removed ->
                    _players.update { players -> players.filterNot { it.id == removed } }
                    ensureSelection(_players.value)
                }

            EventTypes.QUEUE_ADDED, EventTypes.QUEUE_UPDATED ->
                data.decode(ServerQueueDto.serializer())?.let { upsertQueue(toWatchQueue(it)) }

            EventTypes.QUEUE_ITEMS_UPDATED ->
                data.decode(ServerQueueDto.serializer())?.let { dto ->
                    upsertQueue(toWatchQueue(dto))
                    if (dto.queueId == _queueItems.value.queueId) queueReloads.emit(dto.queueId)
                }

            // One per second for the playing queue, which is the watch's whole position model:
            // nothing is interpolated here, and the UI carries the position forward itself.
            EventTypes.QUEUE_TIME_UPDATED -> {
                val queueId = message["object_id"]?.jsonPrimitive?.contentOrNull ?: return
                val seconds = data?.jsonPrimitive?.doubleOrNull ?: return
                _queues.update { queues ->
                    queues[queueId]
                        ?.let { queues + (queueId to it.copy(elapsedSeconds = seconds)) }
                        ?: queues
                }
            }

            else -> Unit
        }
    }

    private fun upsertPlayer(player: WatchPlayer) {
        _players.update { players ->
            val index = players.indexOfFirst { it.id == player.id }
            if (index < 0) players + player else players.toMutableList().apply { set(index, player) }
        }
        ensureSelection(_players.value)
    }

    private fun upsertQueue(queue: WatchQueue) {
        _queues.update { it + (queue.id to queue) }
    }

    private fun updatePlayer(playerId: String, transform: (WatchPlayer) -> WatchPlayer) {
        _players.update { players ->
            players.map { if (it.id == playerId) transform(it) else it }
        }
    }

    /**
     * Keeps a usable player selected: the stored one while it is still there, otherwise the
     * one that is playing, otherwise the first the server can reach.
     */
    private fun ensureSelection(players: List<WatchPlayer>) {
        val current = _selectedPlayerId.value
        if (players.any { it.id == current && it.isSelectable }) return
        val fallback = players.firstOrNull { it.isSelectable && it.isPlaying }
            ?: players.firstOrNull { it.isSelectable }
            ?: return
        _selectedPlayerId.value = fallback.id
        preferences.selectedPlayerId = fallback.id
    }

    private fun nowPlaying(
        player: WatchPlayer,
        queues: Map<String, WatchQueue>,
    ): WatchNowPlaying? {
        val queue = player.queueId?.let { queues[it] }
        val title = queue?.currentTitle ?: player.title
        val artist = queue?.currentArtist ?: player.artist
        val image = queue?.currentImageUrl ?: player.imageUrl
        val duration = queue?.currentDuration ?: player.duration
        if (title == null) return null
        return WatchNowPlaying(
            title = title,
            artist = artist,
            imageUrl = image,
            isPlaying = player.isPlaying,
            positionSeconds = queue?.elapsedSeconds ?: 0.0,
            durationSeconds = duration,
        )
    }

    private fun toWatchPlayer(dto: ServerPlayerDto): WatchPlayer {
        val members = dto.groupMembers.orEmpty()
        val activeSource = dto.sourceList?.firstOrNull { it.id == dto.activeSource }
        return WatchPlayer(
            id = dto.playerId,
            name = dto.displayName.ifEmpty { dto.playerId },
            isGroup = dto.type == PLAYER_TYPE_GROUP,
            memberCount = members.size,
            isAvailable = dto.available,
            isListed = dto.enabled && dto.hidden != true && dto.hideInUi != true,
            isPlaying = dto.state == PLAYER_STATE_PLAYING,
            queueId = dto.activeSource ?: dto.currentMedia?.sourceId,
            canSetVolume = members.isNotEmpty() ||
                dto.supportedFeatures.contains(PlayerFeatures.VOLUME_SET),
            // A player that does not carry the feature itself can still get it from the
            // source it is playing, which is how a plugin source reports its transport.
            canNextPrevious = dto.supportedFeatures.contains(PlayerFeatures.NEXT_PREVIOUS) ||
                activeSource?.canNextPrevious == true,
            volume = (if (members.isNotEmpty()) dto.groupVolume else dto.volumeLevel)
                ?.toInt()?.coerceIn(0, MAX_VOLUME),
            title = dto.currentMedia?.title,
            artist = dto.currentMedia?.artist,
            imageUrl = dto.currentMedia?.imageUrl?.let(config::rebaseImageUrl),
            duration = dto.currentMedia?.duration,
        )
    }

    private fun toWatchQueue(dto: ServerQueueDto): WatchQueue {
        val current = dto.currentItem
        return WatchQueue(
            id = dto.queueId,
            currentItemId = current?.queueItemId,
            currentIndex = dto.currentIndex ?: 0,
            itemCount = dto.items ?: 0,
            ended = dto.ended,
            elapsedSeconds = dto.elapsedTime ?: 0.0,
            currentTitle = current?.displayTitle(),
            currentArtist = current?.displayArtist(),
            currentImageUrl = current?.artworkUrl(),
            currentDuration = current?.duration,
        )
    }

    private fun toWatchQueueItem(dto: ServerQueueItemDto) = WatchQueueItem(
        id = dto.queueItemId,
        title = dto.displayTitle() ?: dto.queueItemId,
        artist = dto.displayArtist(),
        imageUrl = dto.artworkUrl(),
    )

    private fun ServerQueueItemDto.displayTitle(): String? =
        mediaItem?.name?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }

    private fun ServerQueueItemDto.displayArtist(): String? =
        mediaItem?.artists?.firstOrNull()?.name?.takeIf { it.isNotBlank() }

    private fun ServerQueueItemDto.artworkUrl(): String? =
        (image ?: mediaItem?.image)?.let { config.artworkUrl(it, schemaVersion) }

    private fun <T> Result<JsonElement?>.decodeList(serializer: KSerializer<T>): List<T>? =
        getOrNull().decode(ListSerializer(serializer))

    private fun <T> JsonElement?.decode(serializer: KSerializer<T>): T? = this?.let { element ->
        try {
            maJson.decodeFromJsonElement(serializer, element)
        } catch (e: SerializationException) {
            logger.w(e) { "Could not decode ${element.toString().take(LOG_PREVIEW)}" }
            null
        }
    }

    private companion object {
        const val PLAYER_TYPE_GROUP = "group"
        const val PLAYER_STATE_PLAYING = "playing"
        const val MAX_VOLUME = 100
        const val QUEUE_WINDOW = 100
        const val QUEUE_CONTEXT_BEFORE = 5
        const val QUEUE_RELOAD_DEBOUNCE_MS = 400L
        const val LOG_PREVIEW = 300
    }
}
