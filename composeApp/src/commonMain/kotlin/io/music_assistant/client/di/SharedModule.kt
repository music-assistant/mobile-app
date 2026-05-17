package io.music_assistant.client.di

import io.music_assistant.client.api.KtorServiceClient
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.LocalPlayerRepository
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.provideSettings
import io.music_assistant.client.ui.compose.auth.AuthenticationViewModel
import io.music_assistant.client.ui.compose.common.DominantColorViewModel
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.LibraryNavCoordinator
import io.music_assistant.client.ui.compose.library.LibraryViewModel
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.NetworkMonitor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun sharedModule(serviceClientConstructor: (SettingsRepository) -> ServiceClient = ::KtorServiceClient) =
    module {
        single { provideSettings() }
        singleOf(::SettingsRepository)
        singleOf(::NetworkMonitor)
        singleOf(serviceClientConstructor) { bind<ServiceClient>() }
        singleOf(::LogSharer)
        single(createdAtStart = true) {
            AuthenticationManager(
                get(),
                get(),
            )
        }  // Eager - needs to start monitoring immediately
        singleOf(::MediaPlayerController)  // Used by MainDataSource for Sendspin
        singleOf(::SendspinClientFactory)   // Factory for creating Sendspin clients
        singleOf(::LocalPlayerRepository)   // Optimistic local player state
        singleOf(::MediaItemFactory)        // Stateless DTO → domain mapper
        singleOf(::PlayerFactory)           // Stateless DTO → domain mapper
        singleOf(::QueueFactory)            // Stateless DTO → domain mapper (depends on MediaItemFactory)
        singleOf(::MediaItemRepository)     // Server DTO/event → client model boundary for UI
        singleOf(::MainDataSource)          // Singleton - held by foreground service
        singleOf(::DominantColorViewModel)  // Singleton - app-wide art-color cache
        viewModelOf(::ThemeViewModel)
        factory { ActionsViewModel(get(), get(), get()) }
        factory { SettingsViewModel(get(), get(), get()) }
        factory { AuthenticationViewModel(get(), get()) }
        single { LibraryNavCoordinator() }
        factory { LibraryViewModel(get(), get(), get(), get(), get()) }
        factory { params ->
            ItemDetailsViewModel(
                get(),
                get(),
                get(),
                get(),
                params[0],
                params[1],
                params[2],
            )
        }
        factory { DspSettingsViewModel(get()) }
        factory { HomeScreenViewModel(get(), get(), get(), get()) }
        factory { SearchViewModel(get(), get(), get()) }
    }

/**
 * Cleanup function to properly close all singleton resources.
 * Call this before stopKoin() to ensure proper resource cleanup.
 */
fun cleanupSingletons() {
    // Cleanup is handled by individual components' lifecycle
}
