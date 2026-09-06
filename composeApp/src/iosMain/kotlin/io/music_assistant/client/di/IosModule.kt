package io.music_assistant.client.di

import io.ktor.client.webrtc.IosWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.data.LocalPlayerAdapter
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.player.local.AudioQueueSink
import io.music_assistant.client.player.local.IosDecoderFactory
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.client.utils.BackgroundUsageGuard
import io.music_assistant.client.utils.IosBackgroundUsageGuard
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun iosModule() = module {
    single { PlatformContext() }
    single<AudioSink> { AudioQueueSink { command -> get<LocalPlayerAdapter>().onRemoteCommand(command) } }
    single<DecoderFactory> { IosDecoderFactory() }
    single<BackgroundUsageGuard> { IosBackgroundUsageGuard() }

    // CarPlay scene-delegate edges (via ServiceClient.onExternalConsumerActive/Inactive) are a
    // precise connect/disconnect signal on iOS — reuse them directly.
    single<CarConnectionMonitor> { IosCarConnectionMonitor(get<ServiceClient>()) }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(IosWebRtc) {}
    }
}
