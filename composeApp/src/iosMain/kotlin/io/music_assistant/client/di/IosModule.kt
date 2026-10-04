package io.music_assistant.client.di

import io.ktor.client.webrtc.IosWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.data.LocalPlayerAdapter
import io.music_assistant.client.data.announcement.AudioEngineCapture
import io.music_assistant.client.data.announcement.MicrophoneCapture
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.player.local.AudioQueueSink
import io.music_assistant.client.player.local.IosDecoderFactory
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.BackgroundUsageGuard
import io.music_assistant.client.utils.HttpClientFactory
import io.music_assistant.client.utils.IosBackgroundUsageGuard
import io.music_assistant.client.utils.IosHttpClientFactory
import io.music_assistant.client.utils.KeychainClientIdentity
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun iosModule() = module {
    single { PlatformContext() }
    single<AudioSink> {
        AudioQueueSink(onRemoteCommand = { command -> get<LocalPlayerAdapter>().onRemoteCommand(command) })
    }
    single<DecoderFactory> { IosDecoderFactory() }
    single<MicrophoneCapture> { AudioEngineCapture() }
    single<BackgroundUsageGuard> { IosBackgroundUsageGuard() }
    single { KeychainClientIdentity() }
    single<HttpClientFactory> {
        IosHttpClientFactory(get<SettingsRepository>().clientCertificateAlias, get())
    }

    // CarPlay scene-delegate edges (via ServiceClient.onExternalConsumerActive/Inactive) are a
    // precise connect/disconnect signal on iOS — reuse them directly.
    single<CarConnectionMonitor> { IosCarConnectionMonitor(get<ServiceClient>()) }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(IosWebRtc) {}
    }
}
