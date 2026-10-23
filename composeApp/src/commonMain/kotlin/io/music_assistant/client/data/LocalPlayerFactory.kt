package io.music_assistant.client.data

import io.music_assistant.client.utils.AppVersion
import io.music_assistant.client.utils.HttpClientFactory
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.audioDispatcher
import io.music_assistant.client.utils.sendspinClock
import io.music_assistant.sendspin.SendspinPlayer
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.DeviceInfo
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.SendspinKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import io.music_assistant.sendspin.api.SendspinPlayer as SendspinPlayerApi

/**
 * Builds the local player for [LocalPlayerAdapter]. The adapter owns the config
 * flow and the pairing callback; the platform ports live here, so a test can
 * hand the adapter a fake player instead.
 */
fun interface LocalPlayerFactory {
    fun create(
        config: StateFlow<LocalPlayerConfig?>,
        approvePairing: suspend (pairingToken: String) -> Unit,
        scope: CoroutineScope,
    ): SendspinPlayerApi
}

/** The production player: the `:sendspin-kmp` module over the platform sink, decoders and key store. */
class SendspinLocalPlayerFactory(
    private val sink: AudioSink,
    private val decoders: DecoderFactory,
    private val keyStore: SendspinKeyStore,
    private val networkMonitor: NetworkMonitor,
    private val httpClientFactory: HttpClientFactory,
    private val appVersion: AppVersion,
) : LocalPlayerFactory {
    override fun create(
        config: StateFlow<LocalPlayerConfig?>,
        approvePairing: suspend (pairingToken: String) -> Unit,
        scope: CoroutineScope,
    ): SendspinPlayerApi = SendspinPlayer(
        config = config,
        deps = SendspinDeps(
            sink = sink,
            decoders = decoders,
            keyStore = keyStore,
            httpClient = httpClientFactory.create(),
            online = networkMonitor.isAvailable,
            approvePairing = approvePairing,
            audioDispatcher = audioDispatcher,
            deviceInfo = DeviceInfo(
                productName = "Mobile Application",
                manufacturer = "Music Assistant",
                softwareVersion = appVersion.name,
            ),
            clock = sendspinClock,
        ),
        scope = scope,
    )
}
