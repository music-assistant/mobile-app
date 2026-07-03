package io.music_assistant.client.di

import io.ktor.client.webrtc.IosWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.player.PlatformContext
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun iosModule() = module {
    single { PlatformContext() }

    // CarPlay scene-delegate edges (via ServiceClient.onExternalConsumerActive/Inactive) are a
    // precise connect/disconnect signal on iOS — reuse them directly.
    single<CarConnectionMonitor> { IosCarConnectionMonitor(get<ServiceClient>()) }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(IosWebRtc) {}
    }
}
