package io.music_assistant.client.di

import io.music_assistant.client.player.PlatformContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun androidModule() = module {
    single { PlatformContext(androidContext()) }
}
