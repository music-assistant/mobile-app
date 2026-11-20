package io.music_assistant.client.di

import io.music_assistant.client.auto.AutoLibrary
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.data.share.PhoneAudioShare
import io.music_assistant.client.services.AndroidCarConnectionMonitor
import io.music_assistant.client.services.PhoneAudioShareService
import io.music_assistant.client.services.SharedMediaSessionManager
import io.music_assistant.client.share.AndroidPhoneAudioShare
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

fun appModule() = module {
    single { AutoLibrary(androidContext(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SharedMediaSessionManager(androidContext(), get(), get()) }
    single<CarConnectionMonitor> { AndroidCarConnectionMonitor(androidContext()) }
    single { AndroidPhoneAudioShare(androidContext(), PhoneAudioShareService::class.java) } bind PhoneAudioShare::class
}
