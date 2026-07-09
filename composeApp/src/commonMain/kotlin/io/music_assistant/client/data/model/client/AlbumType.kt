package io.music_assistant.client.data.model.client

import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.album_type_album
import musicassistantclient.composeapp.generated.resources.album_type_compilation
import musicassistantclient.composeapp.generated.resources.album_type_ep
import musicassistantclient.composeapp.generated.resources.album_type_live
import musicassistantclient.composeapp.generated.resources.album_type_single
import musicassistantclient.composeapp.generated.resources.album_type_soundtrack
import org.jetbrains.compose.resources.StringResource

/**
 * Mirrors the server's `AlbumType`, used as the `album_types` library filter for
 * albums. `UNKNOWN` is intentionally omitted (not a useful filter); [serverValue]s
 * match `music_assistant_models`.
 */
enum class AlbumType(val serverValue: String) {
    ALBUM("album"),
    SINGLE("single"),
    LIVE("live"),
    SOUNDTRACK("soundtrack"),
    COMPILATION("compilation"),
    EP("ep"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): AlbumType? = raw?.let { byServerValue[it] }
    }
}

fun AlbumType.stringResource(): StringResource = when (this) {
    AlbumType.ALBUM -> Res.string.album_type_album
    AlbumType.SINGLE -> Res.string.album_type_single
    AlbumType.LIVE -> Res.string.album_type_live
    AlbumType.SOUNDTRACK -> Res.string.album_type_soundtrack
    AlbumType.COMPILATION -> Res.string.album_type_compilation
    AlbumType.EP -> Res.string.album_type_ep
}
