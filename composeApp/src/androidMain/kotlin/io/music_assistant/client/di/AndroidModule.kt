package io.music_assistant.client.di

import io.ktor.client.webrtc.AndroidWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.player.local.AndroidDecoderFactory
import io.music_assistant.client.player.local.AudioTrackSink
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.SystemMonotonicClock
import io.music_assistant.client.utils.AndroidBackgroundUsageGuard
import io.music_assistant.client.utils.BackgroundUsageGuard
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun androidModule() = module {
    single { PlatformContext(androidContext()) }
    single<AudioSink> { AudioTrackSink(androidContext(), SystemMonotonicClock) }
    single<DecoderFactory> { AndroidDecoderFactory() }
    single<BackgroundUsageGuard> { AndroidBackgroundUsageGuard(androidContext()) }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(AndroidWebRtc) {
            context = androidContext()
        }
    }
}
