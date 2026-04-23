package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import io.music_assistant.client.player.sendspin.audio.Codec
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun Codec.localizedTitle(): String = when (this) {
    Codec.OPUS -> stringResource(Res.string.codec_opus)
    Codec.FLAC -> stringResource(Res.string.codec_flac)
    Codec.PCM -> stringResource(Res.string.codec_pcm)
}
