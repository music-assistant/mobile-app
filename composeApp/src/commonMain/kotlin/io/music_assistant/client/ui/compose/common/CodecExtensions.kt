package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import io.music_assistant.sendspin.wire.AudioCodec
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.codec_flac
import musicassistantclient.composeapp.generated.resources.codec_opus
import musicassistantclient.composeapp.generated.resources.codec_pcm
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioCodec.localizedTitle(): String = when (this) {
    AudioCodec.OPUS -> stringResource(Res.string.codec_opus)
    AudioCodec.FLAC -> stringResource(Res.string.codec_flac)
    AudioCodec.PCM -> stringResource(Res.string.codec_pcm)
}
