package io.music_assistant.client.di

import io.music_assistant.client.player.PlatformContext
import org.koin.dsl.module

fun iosModule() = module {
    single { PlatformContext() }
}
