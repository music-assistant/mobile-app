package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.DspConfig
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Request @OptIn(ExperimentalUuidApi::class) constructor(
    @SerialName("command") val command: String,
    @SerialName("args") val args: JsonObject? = null,
    @SerialName("message_id") val messageId: String = Uuid.random().toString(),
) {
    data object Player {
        fun all() = Request(command = APICommands.PLAYERS_ALL)

        fun simpleCommand(
            playerId: String,
            command: String,
        ) = Request(
            command = APICommands.playersCmd(command),
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
            },
        )

        fun seek(
            queueId: String,
            position: Long,
        ) = Request(
            command = APICommands.PLAYERS_CMD_SEEK,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(queueId))
                put("position", JsonPrimitive(position))
            },
        )

        fun setVolume(
            playerId: String,
            volumeLevel: Double,
        ) = Request(
            command = APICommands.PLAYERS_CMD_VOLUME_SET,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("volume_level", JsonPrimitive(volumeLevel))
            },
        )

        fun setGroupVolume(
            playerId: String,
            volumeLevel: Double,
        ) = Request(
            command = APICommands.PLAYERS_CMD_GROUP_VOLUME,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("volume_level", JsonPrimitive(volumeLevel))
            },
        )

        fun setMute(
            playerId: String,
            muted: Boolean,
        ) = Request(
            command = APICommands.PLAYERS_CMD_VOLUME_MUTE,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("muted", JsonPrimitive(muted))
            },
        )

        fun setGroupMembers(
            playerId: String,
            playersToAdd: List<String>?,
            playersToRemove: List<String>?,
        ) = Request(
            command = APICommands.PLAYERS_CMD_SET_MEMBERS,
            args = buildJsonObject {
                put("target_player", JsonPrimitive(playerId))
                playersToAdd?.let {
                    put(
                        "player_ids_to_add",
                        myJson.decodeFromString<JsonArray>(myJson.encodeToString(it)),
                    )
                }
                playersToRemove?.let {
                    put(
                        "player_ids_to_remove",
                        myJson.decodeFromString<JsonArray>(myJson.encodeToString(it)),
                    )
                }
            },
        )
    }

    data object Queue {
        fun all() = Request(command = APICommands.PLAYER_QUEUES_ALL)

        fun items(
            queueId: String,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_ITEMS,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
            },
        )

        fun moveItem(
            queueId: String,
            queueItemId: String,
            positionShift: Int,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_MOVE_ITEM,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("queue_item_id", JsonPrimitive(queueItemId))
                put("pos_shift", JsonPrimitive(positionShift))
            },
        )

        fun removeItem(
            queueId: String,
            queueItemId: String,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_DELETE_ITEM,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("item_id_or_index", JsonPrimitive(queueItemId))
            },
        )

        fun clear(
            queueId: String,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_CLEAR,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
            },
        )

        fun playIndex(
            queueId: String,
            queueItemId: String,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_PLAY_INDEX,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("index", JsonPrimitive(queueItemId))
            },
        )

        fun transfer(
            sourceId: String,
            targetId: String,
            autoplay: Boolean,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_TRANSFER,
            args = buildJsonObject {
                put("source_queue_id", JsonPrimitive(sourceId))
                put("target_queue_id", JsonPrimitive(targetId))
                put("auto_play", JsonPrimitive(autoplay))
            },
        )

        fun setRepeatMode(
            queueId: String,
            repeatMode: RepeatMode,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_REPEAT,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("repeat_mode", JsonPrimitive(repeatMode.name.lowercase()))
            },
        )

        fun setShuffle(
            queueId: String,
            enabled: Boolean,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_SHUFFLE,
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("shuffle_enabled", JsonPrimitive(enabled))
            },
        )
    }

    data object Playlist {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_PLAYLISTS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_PLAYLISTS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )

        fun create(name: String) = Request(
            command = APICommands.MUSIC_PLAYLISTS_CREATE_PLAYLIST,
            args = buildJsonObject {
                put("name", JsonPrimitive(name.trim()))
            },
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            forceRefresh: Boolean? = null,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_PLAYLISTS_PLAYLIST_TRACKS,
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                put("provider_instance_id_or_domain", JsonPrimitive(providerInstanceIdOrDomain))
                forceRefresh?.let { put("force_refresh", JsonPrimitive(it)) }
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )

        fun addTracks(playlistId: String, trackUris: List<String>) = Request(
            command = APICommands.MUSIC_PLAYLISTS_ADD_PLAYLIST_TRACKS,
            args = buildJsonObject {
                put("db_playlist_id", JsonPrimitive(playlistId))
                put("uris", myJson.decodeFromString<JsonArray>(myJson.encodeToString(trackUris)))
            },
        )

        fun removeTracks(playlistId: String, positions: List<Int>) = Request(
            command = APICommands.MUSIC_PLAYLISTS_REMOVE_PLAYLIST_TRACKS,
            args = buildJsonObject {
                put("db_playlist_id", JsonPrimitive(playlistId))
                put(
                    "positions_to_remove",
                    myJson.decodeFromString<JsonArray>(myJson.encodeToString(positions)),
                )
            },
        )
    }

    data object Podcast {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_PODCASTS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_PODCASTS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )

        fun getEpisodes(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            APICommands.MUSIC_PODCASTS_PODCAST_EPISODES,
            itemId,
            providerInstanceIdOrDomain,
            inLibraryOnly,
        )
    }

    data object RadioStation {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_RADIOS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_RADIOS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )
    }

    data object Audiobook {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_AUDIOBOOKS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_AUDIOBOOKS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )
    }

    data object Genre {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_GENRES, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_GENRES_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )

        fun overview(
            itemId: String,
            providerInstanceIdOrDomain: String? = null,
            limit: Int = 25,
        ) = Request(
            command = APICommands.MUSIC_GENRES_OVERVIEW,
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                providerInstanceIdOrDomain?.let {
                    put("provider_instance_id_or_domain", JsonPrimitive(it))
                }
                put("limit", JsonPrimitive(limit))
            },
        )
    }

    data object Artist {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_ARTISTS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
            albumArtistsOnly: Boolean = false,
        ) = Request(
            command = APICommands.MUSIC_ARTISTS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
                put("album_artists_only", JsonPrimitive(albumArtistsOnly))
            },
        )

        fun getAlbums(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            APICommands.MUSIC_ARTISTS_ARTIST_ALBUMS,
            itemId,
            providerInstanceIdOrDomain,
            inLibraryOnly,
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            APICommands.MUSIC_ARTISTS_ARTIST_TRACKS,
            itemId,
            providerInstanceIdOrDomain,
            inLibraryOnly,
        )
    }

    data object Album {
        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get(APICommands.KIND_ALBUMS, itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            APICommands.MUSIC_ALBUMS_ALBUM_TRACKS,
            itemId,
            providerInstanceIdOrDomain,
            inLibraryOnly,
        )
    }

    data object Track {
        fun list(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_TRACKS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )
    }

    data object Library {
        internal fun get(
            kind: String,
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Request(
            command = APICommands.musicGet(kind),
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                put("provider_instance_id_or_domain", JsonPrimitive(providerInstanceIdOrDomain))
            },
        )

        fun add(
            itemUri: String,
        ) = Request(
            command = APICommands.MUSIC_LIBRARY_ADD_ITEM,
            args = buildJsonObject {
                put("item", JsonPrimitive(itemUri))
            },
        )

        fun remove(
            itemId: String,
            mediaType: MediaType,
        ) = Request(
            command = APICommands.MUSIC_LIBRARY_REMOVE_ITEM,
            args = buildJsonObject {
                put("library_item_id", JsonPrimitive(itemId))
                put("media_type", JsonPrimitive(mediaType.name.lowercase()))
            },
        )

        fun play(
            media: List<String>,
            queueOrPlayerId: String,
            option: QueueOption,
            radioMode: Boolean,
            startItem: String? = null,
        ) = Request(
            command = APICommands.PLAYER_QUEUES_PLAY_MEDIA,
            args = buildJsonObject {
                put("media", JsonArray(media.map { JsonPrimitive(it) }))
                put("option", JsonPrimitive(option.name.lowercase()))
                put("radio_mode", JsonPrimitive(radioMode))
                put("queue_id", JsonPrimitive(queueOrPlayerId))
                startItem?.let { put("start_item", JsonPrimitive(it)) }
            },
        )

        fun addFavorite(
            itemUri: String,
        ) = Request(
            command = APICommands.MUSIC_FAVORITES_ADD_ITEM,
            args = buildJsonObject {
                put("item", JsonPrimitive(itemUri))
            },
        )

        fun removeFavorite(
            itemId: String,
            mediaType: MediaType,
        ) = Request(
            command = APICommands.MUSIC_FAVORITES_REMOVE_ITEM,
            args = buildJsonObject {
                put("library_item_id", JsonPrimitive(itemId))
                put("media_type", JsonPrimitive(mediaType.name.lowercase()))
            },
        )

        fun markPlayed(
            itemUri: String,
        ) = Request(
            command = APICommands.MUSIC_MARK_ITEM_PLAYED,
            args = buildJsonObject {
                put("media_item", JsonPrimitive(itemUri))
            },
        )

        fun markUnplayed(
            itemUri: String,
        ) = Request(
            command = APICommands.MUSIC_MARK_ITEM_UNPLAYED,
            args = buildJsonObject {
                put("media_item", JsonPrimitive(itemUri))
            },
        )

        fun search(
            query: String,
            mediaTypes: List<MediaType>,
            limit: Int = 20,
            libraryOnly: Boolean,
        ) = Request(
            command = APICommands.MUSIC_SEARCH,
            args = buildJsonObject {
                put("search_query", JsonPrimitive(query.replace("-", " ")))
                put(
                    "media_types",
                    myJson.decodeFromString<JsonArray>(myJson.encodeToString(mediaTypes)),
                )
                put("limit", JsonPrimitive(limit))
                put("library_only", JsonPrimitive(libraryOnly))
            },
        )

        fun recommendations() = Request(command = APICommands.MUSIC_RECOMMENDATIONS)

        fun providersManifests() = Request(command = APICommands.PROVIDERS_MANIFESTS)

        internal fun subItems(
            command: String,
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Request(
            command = command,
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                put("provider_instance_id_or_domain", JsonPrimitive(providerInstanceIdOrDomain))
                put("in_library_only", JsonPrimitive(inLibraryOnly))
            },
        )
    }

    data object Auth {
        fun providers() = Request(command = APICommands.AUTH_PROVIDERS)

        fun authorizationUrl(providerId: String, returnUrl: String? = null) = Request(
            command = APICommands.AUTH_AUTHORIZATION_URL,
            args = buildJsonObject {
                put("provider_id", JsonPrimitive(providerId))
                returnUrl?.let { put("return_url", JsonPrimitive(it)) }
            },
        )

        fun login(username: String, password: String, deviceName: String) = Request(
            command = APICommands.AUTH_LOGIN,
            args = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("password", JsonPrimitive(password))
                put("device_name", JsonPrimitive(deviceName))
            },
        )

        fun logout() = Request(command = APICommands.AUTH_LOGOUT)

        fun authorize(token: String, deviceName: String) = Request(
            command = APICommands.AUTH,
            args = buildJsonObject {
                put("token", JsonPrimitive(token))
                put("device_name", JsonPrimitive(deviceName))
            },
        )
    }

    data object Dsp {
        fun getPlayerConfig(playerId: String) = Request(
            command = APICommands.CONFIG_PLAYERS_DSP_GET,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
            },
        )

        fun savePlayerConfig(playerId: String, config: DspConfig) = Request(
            command = APICommands.CONFIG_PLAYERS_DSP_SAVE,
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("config", myJson.encodeToJsonElement(DspConfig.serializer(), config))
            },
        )

        fun getPresets() = Request(command = APICommands.CONFIG_DSP_PRESETS_GET)
    }
}
