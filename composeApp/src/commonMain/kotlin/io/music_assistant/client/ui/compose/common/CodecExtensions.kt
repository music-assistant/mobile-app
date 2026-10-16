package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import io.music_assistant.sendspin.api.AudioCodec
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.codec_flac_description
import musicassistantclient.composeapp.generated.resources.codec_opus_description
import musicassistantclient.composeapp.generated.resources.codec_pcm_description
import org.jetbrains.compose.resources.stringResource

/** Codec names are format identifiers and are the same in every language. */
val AudioCodec.displayName: String
    get() = when (this) {
        AudioCodec.OPUS -> "Opus"
        AudioCodec.FLAC -> "FLAC"
        AudioCodec.PCM -> "PCM"
    }

@Composable
fun AudioCodec.localizedDescription(): String = when (this) {
    AudioCodec.OPUS -> stringResource(Res.string.codec_opus_description)
    AudioCodec.FLAC -> stringResource(Res.string.codec_flac_description)
    AudioCodec.PCM -> stringResource(Res.string.codec_pcm_description)
}
