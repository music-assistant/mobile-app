package io.music_assistant.client.api

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
    @SerialName("message_id") val messageId: String = Uuid.random().toString()
) {
    data object Player {
        fun all() = Request(command = "players/all")

        fun simpleCommand(
            playerId: String,
            command: String
        ) = Request(
            command = "players/cmd/$command",
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
            }
        )

        fun seek(
            queueId: String,
            position: Long,
        ) = Request(
            command = "players/cmd/seek",
            args = buildJsonObject {
                put("player_id", JsonPrimitive(queueId))
                put("position", JsonPrimitive(position))
            })

        fun setVolume(
            playerId: String,
            volumeLevel: Double,
        ) = Request(
            command = "players/cmd/volume_set",
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("volume_level", JsonPrimitive(volumeLevel))
            }
        )

        fun setGroupVolume(
            playerId: String,
            volumeLevel: Double,
        ) = Request(
            command = "players/cmd/group_volume",
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("volume_level", JsonPrimitive(volumeLevel))
            }
        )

        fun setMute(
            playerId: String,
            muted: Boolean,
        ) = Request(
            command = "players/cmd/volume_mute",
            args = buildJsonObject {
                put("player_id", JsonPrimitive(playerId))
                put("muted", JsonPrimitive(muted))
            }
        )

        fun setGroupMembers(
            playerId: String,
            playersToAdd: List<String>?,
            playersToRemove: List<String>?,
        ) = Request(
            command = "players/cmd/set_members",
            args = buildJsonObject {
                put("target_player", JsonPrimitive(playerId))
                playersToAdd?.let {
                    put(
                        "player_ids_to_add",
                        myJson.decodeFromString<JsonArray>(myJson.encodeToString(it))
                    )
                }
                playersToRemove?.let {
                    put(
                        "player_ids_to_remove",
                        myJson.decodeFromString<JsonArray>(myJson.encodeToString(it))
                    )
                }
            }
        )
    }

    data object Queue {

        fun all() = Request(command = "player_queues/all")

        fun items(
            queueId: String,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
        ) = Request(
            command = "player_queues/items",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
            })

        fun moveItem(
            queueId: String,
            queueItemId: String,
            positionShift: Int,
        ) = Request(
            command = "player_queues/move_item",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("queue_item_id", JsonPrimitive(queueItemId))
                put("pos_shift", JsonPrimitive(positionShift))
            })

        fun removeItem(
            queueId: String,
            queueItemId: String,
        ) = Request(
            command = "player_queues/delete_item",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("item_id_or_index", JsonPrimitive(queueItemId))
            })

        fun clear(
            queueId: String,
        ) = Request(
            command = "player_queues/clear",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
            })

        fun playIndex(
            queueId: String,
            queueItemId: String,
        ) = Request(
            command = "player_queues/play_index",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("index", JsonPrimitive(queueItemId))
            })

        fun transfer(
            sourceId: String,
            targetId: String,
            autoplay: Boolean,
        ) = Request(
            command = "player_queues/transfer",
            args = buildJsonObject {
                put("source_queue_id", JsonPrimitive(sourceId))
                put("target_queue_id", JsonPrimitive(targetId))
                put("auto_play", JsonPrimitive(autoplay))
            })

        fun setRepeatMode(
            queueId: String,
            repeatMode: RepeatMode,
        ) = Request(
            command = "player_queues/repeat",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("repeat_mode", JsonPrimitive(repeatMode.name.lowercase()))
            })

        fun setShuffle(
            queueId: String,
            enabled: Boolean,
        ) = Request(
            command = "player_queues/shuffle",
            args = buildJsonObject {
                put("queue_id", JsonPrimitive(queueId))
                put("shuffle_enabled", JsonPrimitive(enabled))
            })
    }

    data object Playlist {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("playlists", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = "music/playlists/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )

        fun create(name: String) = Request(
            command = "music/playlists/create_playlist",
            args = buildJsonObject {
                put("name", JsonPrimitive(name.trim()))
            }
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            forceRefresh: Boolean? = null,
        ) = Request(
            command = "music/playlists/playlist_tracks",
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                put("provider_instance_id_or_domain", JsonPrimitive(providerInstanceIdOrDomain))
                forceRefresh?.let { put("force_refresh", JsonPrimitive(it)) }

            }
        )

        fun addTracks(playlistId: String, trackUris: List<String>) = Request(
            command = "music/playlists/add_playlist_tracks",
            args = buildJsonObject {
                put("db_playlist_id", JsonPrimitive(playlistId))
                put("uris", myJson.decodeFromString<JsonArray>(myJson.encodeToString(trackUris)))
            }
        )

        fun removeTracks(playlistId: String, positions: List<Int>) = Request(
            command = "music/playlists/remove_playlist_tracks",
            args = buildJsonObject {
                put("db_playlist_id", JsonPrimitive(playlistId))
                put(
                    "positions_to_remove",
                    myJson.decodeFromString<JsonArray>(myJson.encodeToString(positions))
                )
            }
        )
    }

    data object Podcast {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("podcasts", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = "music/podcasts/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )

        fun getEpisodes(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            "music/podcasts/podcast_episodes",
            itemId, providerInstanceIdOrDomain, inLibraryOnly
        )
    }

    data object RadioStation {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("radios", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = "music/radios/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )
    }

    data object Audiobook {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("audiobooks", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = "music/audiobooks/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )
    }

    data object Artist {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("artists", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
            albumArtistsOnly: Boolean = false,
        ) = Request(
            command = "music/artists/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
                put("album_artists_only", JsonPrimitive(albumArtistsOnly))
            }
        )

        fun getAlbums(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            "music/artists/artist_albums",
            itemId, providerInstanceIdOrDomain, inLibraryOnly
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            "music/artists/artist_tracks",
            itemId, providerInstanceIdOrDomain, inLibraryOnly
        )
    }

    data object Album {

        fun get(
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Library.get("albums", itemId, providerInstanceIdOrDomain)

        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = "music/albums/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )

        fun getTracks(
            itemId: String,
            providerInstanceIdOrDomain: String,
            inLibraryOnly: Boolean = false,
        ) = Library.subItems(
            "music/albums/album_tracks",
            itemId, providerInstanceIdOrDomain, inLibraryOnly
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
            command = "music/tracks/library_items",
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            }
        )
    }

    data object Library {

        internal fun get(
            kind: String,
            itemId: String,
            providerInstanceIdOrDomain: String,
        ) = Request(
            command = "music/$kind/get",
            args = buildJsonObject {
                put("item_id", JsonPrimitive(itemId))
                put("provider_instance_id_or_domain", JsonPrimitive(providerInstanceIdOrDomain))
            }
        )

        fun add(
            itemUri: String,
        ) = Request(
            command = "music/library/add_item",
            args = buildJsonObject {
                put("item", JsonPrimitive(itemUri))
            }
        )

        fun remove(
            itemId: String,
            mediaType: MediaType,
        ) = Request(
            command = "music/library/remove_item",
            args = buildJsonObject {
                put("library_item_id", JsonPrimitive(itemId))
                put("media_type", JsonPrimitive(mediaType.name.lowercase()))
            }
        )

        fun play(
            media: List<String>,
            queueOrPlayerId: String,
            option: QueueOption,
            radioMode: Boolean,
            startItem: String? = null,
        ) = Request(
            command = "player_queues/play_media",
            args = buildJsonObject {
                put("media", JsonArray(media.map { JsonPrimitive(it) }))
                put("option", JsonPrimitive(option.name.lowercase()))
                put("radio_mode", JsonPrimitive(radioMode))
                put("queue_id", JsonPrimitive(queueOrPlayerId))
                startItem?.let { put("start_item", JsonPrimitive(it)) }
            }
        )

        fun addFavorite(
            itemUri: String,
        ) = Request(
            command = "music/favorites/add_item",
            args = buildJsonObject {
                put("item", JsonPrimitive(itemUri))
            }
        )

        fun removeFavorite(
            itemId: String,
            mediaType: MediaType,
        ) = Request(
            command = "music/favorites/remove_item",
            args = buildJsonObject {
                put("library_item_id", JsonPrimitive(itemId))
                put("media_type", JsonPrimitive(mediaType.name.lowercase()))
            })

        fun markPlayed(
            itemUri: String,
        ) = Request(
            command = "music/mark_item_played",
            args = buildJsonObject {
                put("media_item", JsonPrimitive(itemUri))
            }
        )

        fun markUnplayed(
            itemUri: String,
        ) = Request(
            command = "music/mark_item_unplayed",
            args = buildJsonObject {
                put("media_item", JsonPrimitive(itemUri))
            }
        )

        fun search(
            query: String,
            mediaTypes: List<MediaType>,
            limit: Int = 20,
            libraryOnly: Boolean
        ) = Request(
            command = "music/search",
            args = buildJsonObject {
                put("search_query", JsonPrimitive(query.replace("-", " ")))
                put(
                    "media_types",
                    myJson.decodeFromString<JsonArray>(myJson.encodeToString(mediaTypes))
                )
                put("limit", JsonPrimitive(limit))
                put("library_only", JsonPrimitive(libraryOnly))
            }
        )

        fun recommendations() = Request(command = "music/recommendations")

        fun providersManifests() = Request(command = "providers/manifests")

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
            }
        )
    }

    data object Auth {

        fun providers() = Request("auth/providers")

        fun authorizationUrl(providerId: String, returnUrl: String? = null) = Request(
            command = "auth/authorization_url",
            args = buildJsonObject {
                put("provider_id", JsonPrimitive(providerId))
                returnUrl?.let { put("return_url", JsonPrimitive(it)) }
            }
        )

        fun login(username: String, password: String, deviceName: String) = Request(
            command = "auth/login",
            args = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("password", JsonPrimitive(password))
                put("device_name", JsonPrimitive(deviceName))
            }
        )

        fun logout() = Request(command = "auth/logout")

        fun authorize(token: String, deviceName: String) = Request(
            command = "auth",
            args = buildJsonObject {
                put("token", JsonPrimitive(token))
                put("device_name", JsonPrimitive(deviceName))
            }
        )
    }
}