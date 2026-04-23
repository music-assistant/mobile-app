package io.music_assistant.client.support

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class FakeServiceClient(private val settingsRepository: SettingsRepository) : ServiceClient {

    private val items = mutableListOf<ServerMediaItem>()
    private val albums: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.ALBUM }
        }

    private val artists: List<ServerMediaItem>
        get() {
            return items.filter { it.mediaType == MediaType.ARTIST }
        }

    val username = "user"
    val password = "password"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    override suspend fun sendRequest(request: Request): Result<Answer> {
        return when (request.command) {
            Request.Auth.providers().command -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            AuthProvider(
                                id = "builtin",
                                type = "builtin",
                                requiresRedirect = false
                            )
                        )
                    )
                )
            }

            Request.Library.recommendations().command -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            ServerMediaItem(
                                itemId = "recently_added_albums",
                                provider = "library",
                                name = "Recently added albums",
                                mediaType = MediaType.FOLDER,
                                items = albums
                            )
                        )
                    )
                )
            }

            Request.Library.search("", emptyList(), libraryOnly = false).command -> {
                Result.success(
                    answer(
                        request = request,
                        result = SearchResult(
                            artists = emptyList(),
                            albums = searchItems(request, items),
                            tracks = emptyList(),
                            playlists = emptyList(),
                            podcasts = emptyList()
                        )
                    )
                )
            }

            Request.Album.get("", "").command -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request, albums)
                    )
                )
            }

            Request.Album.listLibrary().command -> {
                Result.success(
                    answer(
                        request = request,
                        result = albums
                    )
                )
            }

            Request.Artist.get("", "").command -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request, artists)
                    )
                )
            }

            Request.Artist.listLibrary().command -> {
                Result.success(
                    answer(
                        request = request,
                        result = artists
                    )
                )
            }

            else -> {
                Result.failure(UnsupportedOperationException())
            }
        }
    }

    override suspend fun login(username: String, password: String) {
        authorize("token", true)
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
                            wasAutoLogin = true
                        )
                    )
                }

                else -> throw IllegalStateException()
            }
        }
    }

    override fun logout() {
        TODO("Not yet implemented")
    }

    override val isReadyForCommands: StateFlow<Boolean>
        get() = TODO("Not yet implemented")

    private val _serverBaseUrl = MutableStateFlow<String?>(null)
    override val serverBaseUrl: StateFlow<String?> = _serverBaseUrl

    override fun forceWebRTCReconnect() {
        TODO("Not yet implemented")
    }

    override val events: Flow<Event<out Any>> = MutableSharedFlow()
    override val webrtcSendspinChannel: DataChannelWrapper?
        get() = TODO("Not yet implemented")

    override fun onAppForeground() {

    }

    override fun onAppBackground() {

    }

    override fun disconnectByUser() {
        TODO("Not yet implemented")
    }

    override fun connect(connection: ConnectionInfo) {
        settingsRepository.updateConnectionInfo(connection)
        val connectionData = ConnectionData(
            serverInfo = ServerInfo(
                serverVersion = "fake",
                schemaVersion = -1,
                baseUrl = "http://homeassistant.example"
            )
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
        TODO("Not yet implemented")
    }

    override fun onExternalConsumerInactive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackInactive() {
        TODO("Not yet implemented")
    }

    fun addToLibrary(vararg items: ServerMediaItem) {
        this.items.addAll(items)
        items.forEach { item ->
            item.artists?.let {
                this.items.addAll(it)
            }
        }
    }

    private fun findItem(
        request: Request,
        items: List<ServerMediaItem>
    ): ServerMediaItem {
        return items.find {
            it.itemId == (request.args!!["item_id"]!! as JsonPrimitive).content
        }!!
    }

    private fun searchItems(
        request: Request,
        items: List<ServerMediaItem>
    ): List<ServerMediaItem> {
        return items.filter {
            it.name.contains(
                (request.args!!["search_query"]!! as JsonPrimitive).content,
                ignoreCase = true
            )
        }
    }
}

private fun answer(request: Request, result: JsonElement): Answer {
    return Answer(
        JsonObject(
            mapOf(
                "message_id" to JsonPrimitive(request.messageId),
                "result" to result
            )
        )
    )
}

private inline fun <reified T> answer(request: Request, result: T): Answer {
    return answer(request, myJson.encodeToJsonElement(result))
}