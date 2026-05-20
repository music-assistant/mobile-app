package io.music_assistant.client.api

object APICommands {
    // Player commands
    const val PLAYERS_ALL = "players/all"
    const val PLAYERS_CMD = "players/cmd"
    const val PLAYERS_CMD_SEEK = "$PLAYERS_CMD/seek"
    const val PLAYERS_CMD_VOLUME_SET = "$PLAYERS_CMD/volume_set"
    const val PLAYERS_CMD_VOLUME_MUTE = "$PLAYERS_CMD/volume_mute"
    const val PLAYERS_CMD_GROUP_VOLUME = "$PLAYERS_CMD/group_volume"
    const val PLAYERS_CMD_GROUP_VOLUME_MUTE = "$PLAYERS_CMD/group_volume_mute"
    const val PLAYERS_CMD_SET_MEMBERS = "$PLAYERS_CMD/set_members"

    // Player Queue commands
    const val PLAYER_QUEUES_ALL = "player_queues/all"
    const val PLAYER_QUEUES_ITEMS = "player_queues/items"
    const val PLAYER_QUEUES_MOVE_ITEM = "player_queues/move_item"
    const val PLAYER_QUEUES_DELETE_ITEM = "player_queues/delete_item"
    const val PLAYER_QUEUES_CLEAR = "player_queues/clear"
    const val PLAYER_QUEUES_PLAY_INDEX = "player_queues/play_index"
    const val PLAYER_QUEUES_TRANSFER = "player_queues/transfer"
    const val PLAYER_QUEUES_REPEAT = "player_queues/repeat"
    const val PLAYER_QUEUES_SHUFFLE = "player_queues/shuffle"
    const val PLAYER_QUEUES_PLAY_MEDIA = "player_queues/play_media"
    const val PLAYER_QUEUES_DONT_STOP_THE_MUSIC = "player_queues/dont_stop_the_music"

    // Playlist commands
    const val MUSIC_PLAYLISTS_LIBRARY_ITEMS = "music/playlists/library_items"
    const val MUSIC_PLAYLISTS_CREATE_PLAYLIST = "music/playlists/create_playlist"
    const val MUSIC_PLAYLISTS_PLAYLIST_TRACKS = "music/playlists/playlist_tracks"
    const val MUSIC_PLAYLISTS_ADD_PLAYLIST_TRACKS = "music/playlists/add_playlist_tracks"
    const val MUSIC_PLAYLISTS_REMOVE_PLAYLIST_TRACKS = "music/playlists/remove_playlist_tracks"

    // Podcast commands
    const val MUSIC_PODCASTS_LIBRARY_ITEMS = "music/podcasts/library_items"
    const val MUSIC_PODCASTS_PODCAST_EPISODES = "music/podcasts/podcast_episodes"

    // Radio Station commands
    const val MUSIC_RADIOS_LIBRARY_ITEMS = "music/radios/library_items"

    // Audiobook commands
    const val MUSIC_AUDIOBOOKS_LIBRARY_ITEMS = "music/audiobooks/library_items"

    // Genre commands
    const val MUSIC_GENRES_LIBRARY_ITEMS = "music/genres/library_items"
    const val MUSIC_GENRES_OVERVIEW = "music/genres/overview"

    // Artist commands
    const val MUSIC_ARTISTS_LIBRARY_ITEMS = "music/artists/library_items"
    const val MUSIC_ARTISTS_ARTIST_ALBUMS = "music/artists/artist_albums"
    const val MUSIC_ARTISTS_ARTIST_TRACKS = "music/artists/artist_tracks"

    // Album commands
    const val MUSIC_ALBUMS_LIBRARY_ITEMS = "music/albums/library_items"
    const val MUSIC_ALBUMS_ALBUM_TRACKS = "music/albums/album_tracks"

    // Track commands
    const val MUSIC_TRACKS_LIBRARY_ITEMS = "music/tracks/library_items"

    // Library commands
    const val MUSIC_LIBRARY_ADD_ITEM = "music/library/add_item"
    const val MUSIC_LIBRARY_REMOVE_ITEM = "music/library/remove_item"
    const val MUSIC_LIBRARY_GET = "music"

    // Favorites commands
    const val MUSIC_FAVORITES_ADD_ITEM = "music/favorites/add_item"
    const val MUSIC_FAVORITES_REMOVE_ITEM = "music/favorites/remove_item"

    // Mark commands
    const val MUSIC_MARK_ITEM_PLAYED = "music/mark_item_played"
    const val MUSIC_MARK_ITEM_UNPLAYED = "music/mark_item_unplayed"

    // Search and recommendations
    const val MUSIC_SEARCH = "music/search"
    const val MUSIC_RECOMMENDATIONS = "music/recommendations"
    const val PROVIDERS_MANIFESTS = "providers/manifests"

    // Auth commands
    const val AUTH_PROVIDERS = "auth/providers"
    const val AUTH_AUTHORIZATION_URL = "auth/authorization_url"
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_LOGOUT = "auth/logout"
    const val AUTH = "auth"

    // DSP commands
    const val CONFIG_PLAYERS_DSP_GET = "config/players/dsp/get"
    const val CONFIG_PLAYERS_DSP_SAVE = "config/players/dsp/save"
    const val CONFIG_DSP_PRESETS_GET = "config/dsp_presets/get"

    // Media type kinds
    const val KIND_ALBUMS = "albums"
    const val KIND_ARTISTS = "artists"
    const val KIND_PLAYLISTS = "playlists"
    const val KIND_PODCASTS = "podcasts"
    const val KIND_RADIOS = "radios"
    const val KIND_AUDIOBOOKS = "audiobooks"
    const val KIND_GENRES = "genres"

    fun musicGet(kind: String) = "$MUSIC_LIBRARY_GET/$kind/get"

    fun playersCmd(command: String) = "$PLAYERS_CMD/$command"
}
