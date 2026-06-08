package io.music_assistant.client.carplay

import io.music_assistant.client.settings.DefaultClickAction
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_all_to_queue
import musicassistantclient.composeapp.generated.resources.action_browse
import musicassistantclient.composeapp.generated.resources.action_insert_next
import musicassistantclient.composeapp.generated.resources.action_insert_next_and_play
import musicassistantclient.composeapp.generated.resources.action_ok
import musicassistantclient.composeapp.generated.resources.action_play_all
import musicassistantclient.composeapp.generated.resources.action_start_radio
import musicassistantclient.composeapp.generated.resources.albums_by_artist
import musicassistantclient.composeapp.generated.resources.browse_subtitle
import musicassistantclient.composeapp.generated.resources.connection_lost
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.loading
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.not_connected_retry
import org.jetbrains.compose.resources.getString

/**
 * CarPlay UI strings resolved once for the active locale from the shared
 * Compose string catalog — the same Lokalise-managed source the rest of the
 * app uses. This keeps CarPlay in lockstep with app translations instead of
 * shipping a separate iOS string store. Swift reads the fields synchronously
 * after [load] completes (template titles are immutable once constructed, so
 * they must be known before the first template is built).
 */
class CarPlayStrings internal constructor(
    val library: String,
    val browse: String,
    val browseSubtitle: String,
    val loading: String,
    val empty: String,
    val playAll: String,
    val addAllToQueue: String,
    val ok: String,
    val offlineAlert: String,
    val disconnected: String,
    val artists: String,
    val albums: String,
    val tracks: String,
    val playlists: String,
    val audiobooks: String,
    val podcasts: String,
    val radio: String,
    private val albumsByArtistTemplate: String,
    private val bulkActionTitlesByName: Map<String, String>,
) {
    /** Localized "Albums by <artist>" drilldown title. */
    fun albumsByArtist(name: String): String =
        albumsByArtistTemplate.replace("%1\$s", name)

    /** Localized title for a bulk-action name (DefaultClickAction.name); falls back to "Play all". */
    fun bulkActionTitle(name: String): String = bulkActionTitlesByName[name] ?: playAll

    companion object {
        suspend fun load(): CarPlayStrings = CarPlayStrings(
            library = getString(Res.string.nav_library),
            browse = getString(Res.string.action_browse),
            browseSubtitle = getString(Res.string.browse_subtitle),
            loading = getString(Res.string.loading),
            empty = getString(Res.string.library_empty),
            playAll = getString(Res.string.action_play_all),
            addAllToQueue = getString(Res.string.action_add_all_to_queue),
            ok = getString(Res.string.action_ok),
            offlineAlert = getString(Res.string.not_connected_retry),
            disconnected = getString(Res.string.connection_lost),
            artists = getString(Res.string.media_type_artists),
            albums = getString(Res.string.media_type_albums),
            tracks = getString(Res.string.media_type_tracks),
            playlists = getString(Res.string.media_type_playlists),
            audiobooks = getString(Res.string.media_type_audiobooks),
            podcasts = getString(Res.string.media_type_podcasts),
            radio = getString(Res.string.media_type_radio),
            albumsByArtistTemplate = getString(Res.string.albums_by_artist),
            bulkActionTitlesByName = mapOf(
                DefaultClickAction.PLAY_NOW.name to getString(Res.string.action_play_all),
                DefaultClickAction.INSERT_NEXT_AND_PLAY.name to getString(Res.string.action_insert_next_and_play),
                DefaultClickAction.INSERT_NEXT.name to getString(Res.string.action_insert_next),
                DefaultClickAction.ADD_TO_QUEUE.name to getString(Res.string.action_add_all_to_queue),
                DefaultClickAction.START_RADIO.name to getString(Res.string.action_start_radio),
            ),
        )
    }
}
