package io.music_assistant.sendspin.consumercheck

import android.app.Application
import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.music_assistant.sendspin.SendspinPlayer
import io.music_assistant.sendspin.android.AndroidDecoderFactory
import io.music_assistant.sendspin.android.AudioTrackSink
import io.music_assistant.sendspin.android.ElapsedRealtimeClock
import io.music_assistant.sendspin.android.SharedPreferencesSendspinKeyStore
import io.music_assistant.sendspin.android.connectivityOnline
import io.music_assistant.sendspin.android.sendspinAudioDispatcher
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.DeviceInfo
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow

class ConsumerCheckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val context: Context = this
        // Verbatim copy of the "Android quick start" in sendspin-kmp/README.md. Keep the two equal.
        val scope = MainScope()
        val config = MutableStateFlow<LocalPlayerConfig?>(
            LocalPlayerConfig(
                endpoint = Endpoint.WebSocket("ws://192.168.1.10:8927/sendspin", WireVersion.SPEC_1_0),
                deviceName = "Kitchen phone",
                codecPreference = listOf(AudioCodec.FLAC, AudioCodec.OPUS),
                bufferSeconds = 5,
                userDelayMs = 0,
            ),
        )
        val deps = SendspinDeps(
            sink = AudioTrackSink(context, ElapsedRealtimeClock),
            decoders = AndroidDecoderFactory(),
            keyStore = SharedPreferencesSendspinKeyStore(context),
            httpClient = HttpClient(OkHttp),
            online = connectivityOnline(context, scope),
            approvePairing = {},
            audioDispatcher = sendspinAudioDispatcher(),
            deviceInfo = DeviceInfo(productName = "My Player", manufacturer = "Example", softwareVersion = "1.0"),
            clock = ElapsedRealtimeClock,
        )
        val player = SendspinPlayer(config, deps, scope)
    }
}
