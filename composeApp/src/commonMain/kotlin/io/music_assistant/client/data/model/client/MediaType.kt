package io.music_assistant.client.data.model.client

import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks

enum class MediaType(val serverValue: String) {
    ARTIST("artist"),
    ALBUM("album"),
    TRACK("track"),
    PLAYLIST("playlist"),
    RADIO("radio"),
    AUDIOBOOK("audiobook"),
    PODCAST("podcast"),
    PODCAST_EPISODE("podcast_episode"),
    GENRE("genre"),
    FOLDER("folder"),
    COLLECTION("collection"),
    FLOW_STREAM("flow_stream"),
    ANNOUNCEMENT("announcement"),
    SOUND_EFFECT("sound_effect"),
    AUDIO_SOURCE("audio_source"),
    UNKNOWN("unknown"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): MediaType? = raw?.let { byServerValue[it] }

        /**
         * The `media_type` choices of the Genres list under the music content type: genres
         * that at least one library item of this type uses. The podcast and audiobook
         * taxonomies have no such choice, because the content type already names their items.
         */
        val genreMediaTypeOptions = listOf(ARTIST, ALBUM, TRACK)
    }
}

fun MediaType.stringResource() = when (this) {
    MediaType.ARTIST -> Res.string.media_type_artists
    MediaType.ALBUM -> Res.string.media_type_albums
    MediaType.TRACK -> Res.string.media_type_tracks
    MediaType.PLAYLIST -> Res.string.media_type_playlists
    MediaType.AUDIOBOOK -> Res.string.media_type_audiobooks
    MediaType.PODCAST -> Res.string.media_type_podcasts
    MediaType.RADIO -> Res.string.media_type_radio
    MediaType.GENRE -> Res.string.media_type_genres
    else -> throw IllegalArgumentException("No string resource for MediaType: $name")
}
