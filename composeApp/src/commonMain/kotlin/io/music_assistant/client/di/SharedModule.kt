package io.music_assistant.client.di

import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.KtorServiceClient
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.CarDspApplier
import io.music_assistant.client.data.LocalPlayerController
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.PlayerPositionTracker
import io.music_assistant.client.data.PlayerRequestFactory
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.imageloader.ImageCacheInvalidator
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.provideSettings
import io.music_assistant.client.ui.compose.auth.AuthenticationViewModel
import io.music_assistant.client.ui.compose.common.DominantColorViewModel
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.ItemListViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.ui.compose.settings.CarActionsViewModel
import io.music_assistant.client.ui.compose.settings.CarDspViewModel
import io.music_assistant.client.ui.compose.settings.DefaultClickActionsViewModel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.NetworkMonitor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun sharedModule(
    serviceClientConstructor: (SettingsRepository, ErrorMessageBus) -> ServiceClient = ::KtorServiceClient,
) =
    module {
        single { provideSettings() }
        singleOf(::SettingsRepository)
        singleOf(::NetworkMonitor)
        singleOf(::ErrorMessageBus)
        singleOf(::DeepLinkBus)
        singleOf(::ImageCacheInvalidator)
        singleOf(serviceClientConstructor) { bind<ServiceClient>() }
        singleOf(::LogSharer)
        single(createdAtStart = true) {
            AuthenticationManager(
                get(),
                get(),
            )
        }  // Eager - needs to start monitoring immediately
        // Expose the AuthCoordinator surface for viewmodels; same singleton instance.
        single<AuthCoordinator> { get<AuthenticationManager>() }
        singleOf(::MediaPlayerController)  // Used by the local (Sendspin) player sink
        singleOf(::SendspinClientFactory)   // Factory for creating Sendspin clients
        single { PlayerPositionTracker() }  // Shared live-position source of truth
        singleOf(::PlayerRequestFactory)    // Pure PlayerAction → Request mapper
        singleOf(::LocalPlayerController)    // Local player: lifecycle + state + commands
        singleOf(::MediaItemFactory)        // Stateless DTO → domain mapper
        singleOf(::PlayerFactory)           // Stateless DTO → domain mapper
        singleOf(::QueueFactory)            // Stateless DTO → domain mapper (depends on MediaItemFactory)
        singleOf(::MediaItemRepository)     // Server DTO/event → client model boundary for UI
        singleOf(::MainDataSource)          // Singleton - held by foreground service
        single(createdAtStart = true) {     // Eager - must observe car edges from launch
            CarDspApplier(get(), get(), get(), get())
        }
        singleOf(::DominantColorViewModel)  // Singleton - app-wide art-color cache
        singleOf(::MdiCodepoints)           // Singleton - MDI name->codepoint table (one-time load)
        viewModelOf(::ThemeViewModel)
        factory { ActionsViewModel(get(), get(), get()) }
        factory { SettingsViewModel(get(), get(), get()) }
        factory { DefaultClickActionsViewModel(get()) }
        factory { CarActionsViewModel(get()) }
        factory { CarDspViewModel(get(), get()) }
        factory {
            AuthenticationViewModel(
                auth = get(),
                sessionStateFlow = get<ServiceClient>().sessionState,
            )
        }
        factory { LibraryCategoriesViewModel(get()) }
        factory { params -> ItemListViewModel(params[0], get(), get(), get(), get()) }
        factory { params -> BrowseViewModel(params.getOrNull<String>(), get(), get(), get()) }
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
