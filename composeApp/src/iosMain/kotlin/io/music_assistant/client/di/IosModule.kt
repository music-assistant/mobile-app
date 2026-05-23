package io.music_assistant.client.di

import io.ktor.client.webrtc.IosWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.player.PlatformContext
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun iosModule() = module {
    single { PlatformContext() }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(IosWebRtc) {}
    }
}
