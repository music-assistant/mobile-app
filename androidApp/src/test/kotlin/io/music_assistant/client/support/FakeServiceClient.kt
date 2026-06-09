package io.music_assistant.client.support

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.ProviderManifest
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.data.model.server.ServerPlayerMedia
import io.music_assistant.client.data.model.server.ServerQueue
import io.music_assistant.client.data.model.server.ServerQueueItem
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueItemsUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueUpdatedEvent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.UniqueIdGenerator
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class FakeServiceClient(private val settingsRepository: SettingsRepository) : ServiceClient {
    private val uniqueIdGenerator = UniqueIdGenerator()

    private val players = mutableListOf<ServerPlayer>()
    private val queues = mutableListOf<ServerQueue>()
    private val queueItems = mutableMapOf<String, List<ServerQueueItem>>()
    private val items = mutableSetOf<ServerMediaItem>()
    private val albums: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.ALBUM.serverValue }
        }

    private val artists: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.ARTIST.serverValue }
        }

    private val tracks: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.TRACK.serverValue }
        }

    private val playlists: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.PLAYLIST.serverValue }
        }

    private val audiobooks: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.AUDIOBOOK.serverValue }
        }

    private val podcasts: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.PODCAST.serverValue }
        }

    private val radios: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.RADIO.serverValue }
        }

    private val genres: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.GENRE.serverValue }
        }

    private val playlistItems = mutableMapOf<String, List<String>>()

    val username = "user"
    val password = "password"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    private val _isReadyForCommands = MutableStateFlow(false)
    override val isReadyForCommands: StateFlow<Boolean> = _isReadyForCommands

    override suspend fun sendRequest(request: Request): Result<Answer> {
        return when (request.command) {
            APICommands.PROVIDERS_MANIFESTS -> {
                Result.success(
                    answer(
                        request = request,
                        result = emptyList<ProviderManifest>(),
                    ),
                )
            }

            APICommands.AUTH_PROVIDERS -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            AuthProvider(
                                id = "builtin",
                                type = "builtin",
                                requiresRedirect = false,
                            ),
                        ),
                    ),
                )
            }

            APICommands.MUSIC_RECOMMENDATIONS -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            ServerMediaItem(
                                itemId = "recently_added_albums",
                                provider = "library",
                                name = "Recently added albums",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = albums,
                            ),
                            ServerMediaItem(
                                itemId = "recently_added_tracks",
                                provider = "library",
                                name = "Recently added tracks",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = tracks,
                            ),
                        ),
                    ),
                )
            }

            APICommands.MUSIC_SEARCH -> {
                Result.success(
                    answer(
                        request = request,
                        result = SearchResult(
                            artists = emptyList(),
                            albums = searchItems(request, "search_query", items),
                            tracks = emptyList(),
                            playlists = emptyList(),
                            podcasts = emptyList(),
                        ),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ALBUMS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request, albums),
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_ALBUM_TRACKS -> {
                val album = findItem(request, albums)

                Result.success(
                    answer(
                        request = request,
                        result = tracks.filter { it.album == album },
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", albums),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ARTISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request, artists),
                    ),
                )
            }

            APICommands.MUSIC_ARTISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", artists),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", playlists),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_PLAYLISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request, playlists),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_PLAYLIST_TRACKS -> {
                val playlistId = request.getArg("item_id")

                Result.success(
                    answer(
                        request = request,
                        result = tracks.filter { playlistItems[playlistId]!!.contains(it.itemId) },
                    ),
                )
            }

            APICommands.MUSIC_TRACKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = tracks,
                    ),
                )
            }

            APICommands.MUSIC_AUDIOBOOKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", audiobooks),
                    ),
                )
            }

            APICommands.MUSIC_PODCASTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", podcasts),
                    ),
                )
            }

            APICommands.MUSIC_RADIOS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", radios),
                    ),
                )
            }

            APICommands.MUSIC_GENRES_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = searchItems(request, "search", genres),
                    ),
                )
            }

            APICommands.PLAYERS_ALL -> {
                Result.success(
                    answer(
                        request = request,
                        result = players,
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_PLAY_MEDIA -> {
                val mediaUri = ((request.args!!["media"] as JsonArray)[0] as JsonPrimitive).content
                val startItemId = request.getArgOrNull("start_item")
                val mediaTracks = items.find { it.uri == mediaUri }?.let { item ->
                    when (MediaType.fromServer(item.mediaType)) {
                        MediaType.ALBUM -> {
                            val albumTracks = tracks.filter { it.album == item }
                            val startIndex = if (startItemId != null) {
                                albumTracks.indexOfFirst { it.itemId == startItemId }
                            } else {
                                0
                            }

                            albumTracks.drop(startIndex)
                        }
                        MediaType.TRACK -> listOf(item)
                        MediaType.PLAYLIST -> {
                            val playlistTracks = tracks.filter { playlistItems[item.itemId]!!.contains(it.itemId) }
                            val startIndex = if (startItemId != null) {
                                playlistTracks.indexOfFirst { it.itemId == startItemId }
                            } else {
                                0
                            }

                            playlistTracks.drop(startIndex)
                        }
                        else -> TODO()
                    }
                } ?: emptyList()

                val queueId = request.getArg("queue_id")
                updateQueue(
                    queueId,
                    mediaTracks.map { ServerQueueItem(uniqueIdGenerator.nextInt().toString(), it) },
                )
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.PLAYING,
                        currentMedia = mediaTracks.firstOrNull()?.let { track ->
                            ServerPlayerMedia(
                                uri = track.uri,
                                mediaType = track.mediaType,
                                title = track.name,
                                queueId = queueId,
                            )
                        },
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.PLAYER_QUEUES_ALL -> {
                Result.success(
                    answer(
                        request = request,
                        result = queues,
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_ITEMS -> {
                val queueId = (request.args!!["queue_id"] as JsonPrimitive).content

                Result.success(
                    answer(
                        request = request,
                        result = queueItems[queueId],
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_CLEAR -> {
                val queueId = (request.args!!["queue_id"] as JsonPrimitive).content
                updateQueue(queueId, emptyList())
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.IDLE,
                        currentMedia = null,
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.playersCmd("play_pause") -> {
                val playerId = (request.args!!["player_id"] as JsonPrimitive).content
                updatePlayer({ it.playerId == playerId }) {
                    it.copy(state = PlayerState.PAUSED)
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.PLAYER_QUEUES_TRANSFER -> {
                val queueId = request.getArg("source_queue_id")
                val targetQueueId = request.getArg("target_queue_id")
                val autoPlay = request.getArg("auto_play").toBoolean()

                val queueItems = queueItems[queueId] ?: emptyList()
                updateQueue(queueId, emptyList())
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.IDLE,
                        currentMedia = null,
                    )
                }

                updateQueue(targetQueueId, queueItems)
                updatePlayer({ it.activeSource == targetQueueId }) {
                    it.copy(
                        state = if (autoPlay) PlayerState.PLAYING else PlayerState.PAUSED,
                        currentMedia = queueItems.firstOrNull()?.mediaItem?.let { track ->
                            ServerPlayerMedia(
                                uri = track.uri,
                                mediaType = track.mediaType,
                                title = track.name,
                                queueId = queueId,
                            )
                        },
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            else -> {
                Result.failure(UnsupportedOperationException())
            }
        }
    }

    private suspend fun updateQueue(
        queueId: String,
        items: List<ServerQueueItem>,
    ) {
        val queueIndex = queues.indexOfFirst { it.queueId == queueId }

        val currentItem = items.firstOrNull()
        queues[queueIndex] =
            queues[queueIndex].copy(currentItem = currentItem)
        queueItems[queueId] = items

        val queue = queues[queueIndex]
        _events.emit(
            QueueUpdatedEvent(
                event = EventType.QUEUE_UPDATED,
                objectId = queue.queueId,
                data = queue,
            ),
        )

        _events.emit(
            QueueItemsUpdatedEvent(
                event = EventType.QUEUE_ITEMS_UPDATED,
                objectId = queue.queueId,
                data = queue,
            ),
        )
    }

    private suspend fun updatePlayer(
        search: (ServerPlayer) -> Boolean,
        update: (ServerPlayer) -> ServerPlayer,
    ) {
        val playerIndex = players.indexOfFirst(search)
        val player = update(players[playerIndex])
        players[playerIndex] = player
        _events.emit(
            PlayerUpdatedEvent(
                event = EventType.PLAYER_UPDATED,
                objectId = player.playerId,
                data = player,
            ),
        )
    }

    override suspend fun login(username: String, password: String) {
        authorize("token", true)
        _isReadyForCommands.value = true
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        _sessionState.update {
            when (it) {
                is SessionState.Connected.Direct -> {
                    SessionState.Connected.Direct(
                        it.connectionInfo,
                        it.connectionData.copy(
                            authProcessState = AuthProcessState.NotStarted,
                            user = User("-1", username, username, "user"),
                            wasAutoLogin = true,
                        ),
                    )
                }

                else -> error("Unhandled request type in FakeServiceClient")
            }
        }
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    private val _serverBaseUrl = MutableStateFlow<String?>(null)
    override val serverBaseUrl: StateFlow<String?> = _serverBaseUrl

    override fun resolveImageUrl(path: String, provider: String, isRemotelyAccessible: Boolean): String? = null

    override fun rebaseServerImageUrl(rawUrl: String): String? = null

    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy? = null

    override fun forceWebRTCReconnect() {
        TODO("Not yet implemented")
    }

    private val _events = MutableSharedFlow<Event<out Any>>()
    override val events: Flow<Event<out Any>> = _events
    override val webrtcSendspinChannel: DataChannelWrapper
        get() = TODO("Not yet implemented")

    override fun onAppForeground() {
    }

    override fun onAppBackground() {
    }

    override val foregroundEvents: Flow<Unit> = emptyFlow()

    override fun disconnectByUser() {
        TODO("Not yet implemented")
    }

    override fun connect(connection: ConnectionInfo) {
        settingsRepository.updateConnectionInfo(connection)
        val connectionData = ConnectionData(
            serverInfo = ServerInfo(
                serverVersion = "fake",
                schemaVersion = -1,
                baseUrl = "http://homeassistant.example",
            ),
        )
        _sessionState.value = SessionState.Connected.Direct(connection, connectionData)
        _serverBaseUrl.value = connectionData.serverInfo?.baseUrl
    }

    override fun connectWebRTC(remoteId: RemoteId) {
        TODO("Not yet implemented")
    }

    override fun onExternalConsumerActive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackActive() {
    }

    override fun onExternalConsumerInactive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackInactive() {
    }

    fun addToLibrary(vararg items: ServerMediaItem) {
        this.items.addAll(items)
        items.forEach { item ->
            item.artists?.let {
                this.items.addAll(it)
            }
        }
        items.forEach { item ->
            item.album?.let {
                this.items.add(it)
            }
        }
    }

    fun addPlayers(vararg players: ServerPlayer) {
        players.forEach { player ->
            player.activeSource?.let {
                this.queues.add(ServerQueue(queueId = it, available = true))
            }
        }

        this.players.addAll(players)
    }

    fun getState(playerId: String): PlayerState? {
        val player = players.find { it.playerId == playerId }
        return player?.state
    }

    fun getCurrentlyPlaying(playerId: String): ServerMediaItem? {
        val player = players.find { it.playerId == playerId }
        return if (player != null) {
            queues.find { it.queueId == player.activeSource }?.currentItem?.mediaItem
        } else {
            null
        }
    }

    private fun findItem(
        request: Request,
        items: List<ServerMediaItem>,
    ): ServerMediaItem {
        return items.find {
            it.itemId == (request.args!!["item_id"]!! as JsonPrimitive).content
        }!!
    }

    private fun searchItems(
        request: Request,
        requestArg: String,
        items: Collection<ServerMediaItem>,
    ): List<ServerMediaItem> {
        return items.filter {
            it.name.contains(
                (request.args!![requestArg]!! as JsonPrimitive).content,
                ignoreCase = true,
            )
        }
    }

    fun getQueueForPlayer(player: ServerPlayer): List<ServerMediaItem> {
        return queueItems[player.activeSource]!!.map { it.mediaItem!! }
    }

    fun setPlaylist(playlist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        playlistItems[playlist.itemId] = tracks.map { it.itemId }
    }
}

private fun answer(request: Request, result: JsonElement): Answer {
    return Answer(
        JsonObject(
            mapOf(
                "message_id" to JsonPrimitive(request.messageId),
                "result" to result,
            ),
        ),
    )
}

private inline fun <reified T> answer(request: Request, result: T): Answer {
    return answer(request, myJson.encodeToJsonElement(result))
}

private fun Request.getArg(arg: String): String {
    return getArgOrNull(arg)!!
}

private fun Request.getArgOrNull(arg: String): String? {
    return (args!![arg] as JsonPrimitive?)?.content
}
