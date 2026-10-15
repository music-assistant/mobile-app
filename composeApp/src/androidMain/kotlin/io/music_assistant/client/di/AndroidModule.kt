package io.music_assistant.client.di

import io.ktor.client.webrtc.AndroidWebRtc
import io.ktor.client.webrtc.WebRtcClient
import io.ktor.utils.io.ExperimentalKtorApi
import io.music_assistant.client.data.announcement.AudioRecordCapture
import io.music_assistant.client.data.announcement.MicrophoneCapture
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.player.local.AndroidDecoderFactory
import io.music_assistant.client.player.local.AudioTrackSink
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AndroidBackgroundUsageGuard
import io.music_assistant.client.utils.AndroidHttpClientFactory
import io.music_assistant.client.utils.BackgroundUsageGuard
import io.music_assistant.client.utils.HttpClientFactory
import io.music_assistant.client.utils.sendspinClock
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.DecoderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

@OptIn(ExperimentalKtorApi::class)
fun androidModule() = module {
    single { PlatformContext(androidContext()) }
    single<AudioSink> { AudioTrackSink(androidContext(), sendspinClock) }
    single<DecoderFactory> { AndroidDecoderFactory() }
    single<MicrophoneCapture> { AudioRecordCapture() }
    single<BackgroundUsageGuard> { AndroidBackgroundUsageGuard(androidContext()) }
    single<HttpClientFactory> {
        AndroidHttpClientFactory(androidContext(), get<SettingsRepository>().clientCertificateAlias)
    }

    // Ktor WebRTC engine — Phase A spike for migration off webrtc-kmp.
    // See plans/let-s-investigate-possible-migration-sequential-pike.md.
    single<WebRtcClient> {
        WebRtcClient(AndroidWebRtc) {
            context = androidContext()
        }
    }
}
