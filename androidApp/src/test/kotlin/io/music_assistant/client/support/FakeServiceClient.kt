package io.music_assistant.client.support

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.DSPSettings
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
import io.music_assistant.client.data.model.server.ServerUser
import io.music_assistant.client.data.model.server.ServerUserPreferences
import io.music_assistant.client.data.model.server.StreamDetails
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueItemsUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueUpdatedEvent
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.UniqueIdGenerator
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.update
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

class FakeServiceClient : ServiceClient {
    private var legacyVersion: LegacyVersion? = null
    private var requestErrors: Boolean = false
    private var connectionError: Exception? = null

    private val uniqueIdGenerator = UniqueIdGenerator()

    private val players = mutableListOf<ServerPlayer>()
    private val playerAudioFormats = mutableMapOf<String, AudioFormat>()
    private val queues = mutableListOf<ServerQueue>()
    private val queueItems = mutableMapOf<String, List<ServerQueueItem>>()
    private val mediaItemStore = FakeMediaItemStore()
    private val shortcuts = mutableListOf<String>()

    val username = "user"
    val password = "password"
    var serverId = "serverId"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    private val _isReadyForCommands = MutableStateFlow(false)
    override val isReadyForCommands: StateFlow<Boolean> = _isReadyForCommands

    override val externalConsumerActive: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun sendRequest(request: Request): Result<Answer> {
        if (requestErrors) {
            return Result.failure(Exception())
        }

        return when (request.command) {
            APICommands.PROVIDERS_MANIFESTS -> {
                Result.success(
                    answer(
                        request = request,
                        result = emptyList<ProviderManifest>(),
                    ),
                )
            }

            APICommands.AUTH_ME -> {
                if (legacyVersion == LegacyVersion.V2_8) {
                    Result.success(
                        answer(
                            request = request,
                            result = emptyMap<String, String>(),
                        ),
                    )
                } else {
                    Result.success(
                        answer(
                            request = request,
                            result = ServerUser(preferences = ServerUserPreferences(shortcuts)),
                        ),
                    )
                }
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

            APICommands.MUSIC_ITEM_BY_URI -> {
                val item = mediaItemStore.getByUri(request.getArg("uri"))!!
                Result.success(answer(request = request, result = item.enrichLibraryItem()))
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
                                items = mediaItemStore.query(mediaType = MediaType.ALBUM),
                            ),
                            ServerMediaItem(
                                itemId = "recently_added_tracks",
                                provider = "library",
                                name = "Recently added tracks",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = mediaItemStore.query(mediaType = MediaType.TRACK),
                            ),
                            ServerMediaItem(
                                itemId = "recently_added_artists",
                                provider = "library",
                                name = "Recently added artists",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = mediaItemStore.query(mediaType = MediaType.ARTIST),
                            ),
                        ),
                    ),
                )
            }

            APICommands.MUSIC_SEARCH -> {
                val mediaTypes =
                    (request.args!!["media_types"] as JsonArray).map { (it as JsonPrimitive).content }
                val libraryOnly = request.getArgOrNull("library_only") == "true"
                val results = mediaItemStore.query(
                    request.getArg("search_query"),
                    inLibraryOnly = libraryOnly,
                )

                val resultsForType: (MediaType) -> List<ServerMediaItem> = {
                    val mediaTypeServerValue = it.serverValue
                    if (mediaTypes.isEmpty() || mediaTypes.contains(mediaTypeServerValue)) {
                        results.filter { it.mediaType == mediaTypeServerValue }.enrichLibraryItems()
                    } else {
                        emptyList()
                    }
                }

                Result.success(
                    answer(
                        request = request,
                        result = SearchResult(
                            artists = resultsForType(MediaType.ARTIST),
                            albums = resultsForType(MediaType.ALBUM),
                            tracks = resultsForType(MediaType.TRACK),
                            playlists = resultsForType(MediaType.PLAYLIST),
                            podcasts = resultsForType(MediaType.PODCAST),
                            audiobooks = resultsForType(MediaType.AUDIOBOOK),
                            radio = resultsForType(MediaType.RADIO),
                            genres = resultsForType(MediaType.GENRE),
                        ),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ALBUMS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).enrichLibraryItem(),
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_ALBUM_TRACKS -> {
                val album = findItem(request)

                Result.success(
                    answer(
                        request = request,
                        result = mediaItemStore.getTracksByAlbum(album),
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.ALBUM).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ARTISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).enrichLibraryItem(),
                    ),
                )
            }

            APICommands.MUSIC_ARTISTS_ARTIST_ALBUMS -> {
                val artist = findItem(request)
                val provider = request.getArg("provider_instance_id_or_domain")
                val albums = mediaItemStore.getAlbumsByArtist(artist, provider)

                Result.success(
                    answer(
                        request = request,
                        result = if (provider == ServerMediaItem.LIBRARY_PROVIDER) {
                            albums.enrichLibraryItems()
                        } else {
                            albums
                        },
                    ),
                )
            }

            APICommands.MUSIC_ARTISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.ARTIST).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.PLAYLIST).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_PLAYLISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).enrichLibraryItem(),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_PLAYLIST_TRACKS -> {
                val playlist = findItem(request)

                Result.success(
                    answer(
                        request = request,
                        result = mediaItemStore.getTracksByPlaylist(playlist),
                    ),
                )
            }

            APICommands.MUSIC_TRACKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = mediaItemStore.query(
                            mediaType = MediaType.TRACK,
                            inLibraryOnly = true,
                        ).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.MUSIC_AUDIOBOOKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.AUDIOBOOK).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.MUSIC_PODCASTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.PODCAST).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.MUSIC_RADIOS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.RADIO).enrichLibraryItems(),
                    ),
                )
            }

            APICommands.MUSIC_GENRES_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.GENRE).enrichLibraryItems(),
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
                val mediaTracks =
                    mediaItemStore.getByUri(mediaUri)?.let { item ->
                        when (MediaType.fromServer(item.mediaType)) {
                            MediaType.ALBUM -> {
                                val albumTracks = mediaItemStore.getTracksByAlbum(item)
                                val startIndex = if (startItemId != null) {
                                    albumTracks.indexOfFirst { it.itemId == startItemId }
                                } else {
                                    0
                                }

                                albumTracks.drop(startIndex)
                            }

                            MediaType.TRACK -> listOf(item)
                            MediaType.PLAYLIST -> {
                                val playlistTracks = mediaItemStore.getTracksByPlaylist(item)
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

            APICommands.MUSIC_ARTISTS_TOP_TRACKS -> {
                val artist = findItem(request)
                val tracks = mediaItemStore.getTracksByArtist(artist, topOnly = true)

                Result.success(
                    answer(
                        request = request,
                        result = tracks,
                    ),
                )
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
        val player = findPlayer { it.activeSource == queueId }.second

        val dsp = legacyVersion.let {
            if (it != null && it <= LegacyVersion.V2_9) {
                mapOf(player.playerId to DSPSettings(outputFormat = playerAudioFormats[player.playerId]))
            } else {
                null
            }
        }

        val firstItem = items.firstOrNull()
        val currentItem = firstItem?.copy(
            streamDetails = firstItem.streamDetails.let { streamDetails ->
                streamDetails?.copy(dsp = dsp) ?: StreamDetails(
                    audioFormat = AudioFormat(),
                    dsp = dsp,
                )
            },
        ) ?: firstItem

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
        val (playerIndex, originalPlayer) = findPlayer(search)
        val updatedPlayer = update(originalPlayer)
        players[playerIndex] = updatedPlayer
        _events.emit(
            PlayerUpdatedEvent(
                event = EventType.PLAYER_UPDATED,
                objectId = updatedPlayer.playerId,
                data = updatedPlayer,
            ),
        )
    }

    private fun findPlayer(search: (ServerPlayer) -> Boolean): Pair<Int, ServerPlayer> {
        val playerIndex = players.indexOfFirst(search)
        val originalPlayer = players[playerIndex]
        return Pair(playerIndex, originalPlayer)
    }

    override suspend fun login(username: String, password: String) {
        if (username == this.username && password == this.password) {
            authorize("token", true)
            _isReadyForCommands.value = true
        } else {
            _sessionState.update { state ->
                (state as SessionState.Connected).update(
                    authProcessState = AuthProcessState.Failed("Invalid username or password"),
                )
            }
        }
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
                            token = token,
                        ),
                    )
                }

                else -> error("Unhandled request type in FakeServiceClient")
            }
        }
    }

    override fun logout() {
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.LoggedOut,
                user = null,
            ) ?: it
        }
    }

    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null

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
        _sessionState.update {
            SessionState.Disconnected.ByUser
        }
    }

    override fun connect(connection: ConnectionInfo) {
        connectionError.let {
            if (it == null) {
                val connectionData = ConnectionData(
                    serverInfo = ServerInfo(
                        serverId = serverId,
                        serverVersion = "fake",
                        schemaVersion = -1,
                        baseUrl = "http://homeassistant.example",
                    ),
                )
                _sessionState.value = SessionState.Connected.Direct(connection, connectionData)
            } else {
                _sessionState.value = SessionState.Disconnected.Error(it)
            }
        }
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

    override fun forceDisconnect(reason: Exception) {
        _sessionState.update {
            SessionState.Disconnected.Error(reason)
        }
    }

    override fun noServer() {
        _sessionState.update { SessionState.Disconnected.NoServerData }
    }

    fun addItems(vararg items: ServerMediaItem) {
        mediaItemStore.addItems(*items)
    }

    fun addToLibrary(vararg items: ServerMediaItem) {
        items.forEach { addToLibrary(it) }
    }

    fun addToLibrary(item: ServerMediaItem) {
        mediaItemStore.addToLibrary(item)
    }

    fun matchItem(libraryItem: ServerMediaItem, providerItem: ServerMediaItem) {
        mediaItemStore.matchItem(libraryItem, providerItem)
    }

    fun addPlayers(vararg players: ServerPlayer) {
        players.forEach { player ->
            player.activeSource?.let {
                this.queues.add(ServerQueue(queueId = it, available = true))
            }
        }

        this.players.addAll(players)
    }

    fun addShortcut(item: ServerMediaItem) {
        shortcuts.add(item.uri!!)
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

    private fun findItem(request: Request): ServerMediaItem {
        val itemId = request.getArg("item_id")
        val provider = request.getArg("provider_instance_id_or_domain")
        return mediaItemStore.getItem(itemId, provider)
    }

    private fun filterLibrary(
        request: Request,
        mediaType: MediaType,
    ): List<ServerMediaItem> {
        val query = request.getArgOrNull("search")
        val favoritesOnly = request.getArgOrNull("favorite") == "true"
        return mediaItemStore.query(
            query = query,
            mediaType = mediaType,
            inLibraryOnly = true,
            favoritesOnly = favoritesOnly,
        )
    }

    fun getQueueForPlayer(player: ServerPlayer): List<ServerMediaItem> {
        return queueItems[player.activeSource]!!.map { it.mediaItem!! }
    }

    fun setPlaylist(playlist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        mediaItemStore.setPlaylist(playlist, *tracks)
    }

    fun setTopTracks(artist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        mediaItemStore.setTopTracks(artist, *tracks)
    }

    fun setRequestErrors(requestError: Boolean) {
        this.requestErrors = requestError
    }

    fun setLegacyVersion(version: LegacyVersion) {
        this.legacyVersion = version
    }

    fun setReconnecting(reconnecting: Boolean) {
        if (reconnecting) {
            _sessionState.update {
                when (it) {
                    is SessionState.Connected.Direct -> {
                        SessionState.Reconnecting.Direct(
                            attempt = 1,
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        } else {
            _sessionState.update {
                when (it) {
                    is SessionState.Reconnecting.Direct -> {
                        SessionState.Connected.Direct(
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        }
    }

    fun setConnectionError(error: Exception?) {
        this.connectionError = error

        if (error != null) {
            _sessionState.value = SessionState.Disconnected.Error(error)
        }
    }

    fun setNetworkAvailable(available: Boolean) {
        if (available) {
            _sessionState.update {
                when (it) {
                    is SessionState.Reconnecting.Direct -> {
                        SessionState.Connected.Direct(
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        } else {
            _sessionState.update {
                when (it) {
                    is SessionState.Connected.Direct -> {
                        SessionState.Reconnecting.Direct(
                            attempt = 1,
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                            isOnline = false,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        }
    }

    fun setPlayerAudioFormat(player: ServerPlayer, audioFormat: AudioFormat) {
        playerAudioFormats[player.playerId] = audioFormat
    }

    private fun ServerMediaItem.enrichLibraryItem(): ServerMediaItem {
        return mediaItemStore.enrichLibraryItem(this)
    }

    private fun List<ServerMediaItem>.enrichLibraryItems(): List<ServerMediaItem> {
        return this.map { mediaItemStore.enrichLibraryItem(it) }
    }

    enum class LegacyVersion {
        V2_8,
        V2_9,
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
