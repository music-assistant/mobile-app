package io.music_assistant.client.di

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.provideSettings
import io.music_assistant.client.ui.compose.auth.AuthenticationViewModel
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.LibraryViewModel
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.ui.theme.ThemeViewModel

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedModule = module {
    single { provideSettings() }
    singleOf(::SettingsRepository)
    singleOf(::ServiceClient)
    single(createdAtStart = true) { AuthenticationManager(get(), get()) }  // Eager - needs to start monitoring immediately
    singleOf(::MediaPlayerController)  // Used by MainDataSource for Sendspin
    singleOf(::SendspinClientFactory)   // Factory for creating Sendspin clients
    singleOf(::MainDataSource)          // Singleton - held by foreground service
    viewModelOf(::ThemeViewModel)
    factory { ActionsViewModel(get(), get()) }
    factory { SettingsViewModel(get(), get()) }
    factory { AuthenticationViewModel(get(), get(), get()) }
    factory { LibraryViewModel(get(), get()) }
    factory { ItemDetailsViewModel(get(), get()) }
    factory { HomeScreenViewModel(get(), get(), get()) }
    factory { SearchViewModel(get(), get()) }
}

/**
 * Cleanup function to properly close all singleton resources.
 * Call this before stopKoin() to ensure proper resource cleanup.
 */
fun cleanupSingletons() {
    // Cleanup is handled by individual components' lifecycle
}