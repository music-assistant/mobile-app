package io.music_assistant.client.services

import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_favorite
import musicassistantclient.composeapp.generated.resources.media_action_forward
import musicassistantclient.composeapp.generated.resources.media_action_next_player
import musicassistantclient.composeapp.generated.resources.media_action_open_app
import musicassistantclient.composeapp.generated.resources.media_action_repeat
import musicassistantclient.composeapp.generated.resources.media_action_rewind
import musicassistantclient.composeapp.generated.resources.media_action_shuffle
import musicassistantclient.composeapp.generated.resources.media_artist_on_player
import musicassistantclient.composeapp.generated.resources.media_queue_now_playing
import musicassistantclient.composeapp.generated.resources.media_unknown_artist
import musicassistantclient.composeapp.generated.resources.media_unknown_track
import org.jetbrains.compose.resources.getString

/**
 * Media session / notification labels resolved once for the active locale from the
 * shared Compose string catalog — the same Lokalise-managed source the rest of the
 * app uses. Mirrors [io.music_assistant.client.carplay.CarPlayStrings]: the session
 * writers are synchronous, so they read these fields after [load] completes (the
 * writer collectors are started only after loading, see SharedMediaSessionManager).
 */
class MediaSessionStrings internal constructor(
    val unknownTrack: String,
    val unknownArtist: String,
    val nowPlaying: String,
    val rewind: String,
    val forward: String,
    val nextPlayer: String,
    val shuffle: String,
    val repeat: String,
    val favorite: String,
    val openApp: String,
    private val artistOnPlayerTemplate: String,
) {
    /** Localized "<artist> (on <player>)" for the multi-player artist suffix. */
    fun artistWithPlayer(artist: String, player: String): String =
        artistOnPlayerTemplate.replace("%1\$s", artist).replace("%2\$s", player)

    companion object {
        suspend fun load(): MediaSessionStrings = MediaSessionStrings(
            unknownTrack = getString(Res.string.media_unknown_track),
            unknownArtist = getString(Res.string.media_unknown_artist),
            nowPlaying = getString(Res.string.media_queue_now_playing),
            rewind = getString(Res.string.media_action_rewind),
            forward = getString(Res.string.media_action_forward),
            nextPlayer = getString(Res.string.media_action_next_player),
            shuffle = getString(Res.string.media_action_shuffle),
            repeat = getString(Res.string.media_action_repeat),
            favorite = getString(Res.string.cd_favorite),
            openApp = getString(Res.string.media_action_open_app),
            artistOnPlayerTemplate = getString(Res.string.media_artist_on_player),
        )
    }
}
