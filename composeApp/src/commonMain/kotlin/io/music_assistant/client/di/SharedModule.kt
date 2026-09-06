package io.music_assistant.client.di

import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.KtorServiceClient
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.connection.ConnectionManager
import io.music_assistant.client.data.CarDspApplier
import io.music_assistant.client.data.LocalPlayerController
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.PlayerPositionTracker
import io.music_assistant.client.data.PlayerRequestFactory
import io.music_assistant.client.data.UserPreferences
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.repository.AiRadioRepository
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ServiceClientMediaItemRepository
import io.music_assistant.client.imageloader.ImageCacheInvalidator
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.player.sendspin.identity.SendspinKeyStore
import io.music_assistant.client.player.sendspin.identity.SettingsSendspinKeyStore
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.provideSecretSettings
import io.music_assistant.client.settings.provideSettings
import io.music_assistant.client.ui.BackgroundRestrictionViewModel
import io.music_assistant.client.ui.SchemaVersionWarningViewModel
import io.music_assistant.client.ui.compose.auth.AuthenticationViewModel
import io.music_assistant.client.ui.compose.common.DominantColorViewModel
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.players.DspSettingsViewModel
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.item.ItemListViewModel
import io.music_assistant.client.ui.compose.item.ViewModeViewModel
import io.music_assistant.client.ui.compose.item.artist.ArtistDetailsViewModel
import io.music_assistant.client.ui.compose.library.AiRadioViewModel
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryListViewModel
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
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier for the secrets store. Its backing file is excluded from the
 * Android backup, so put a value here only when it authenticates to the
 * user's server or identifies it.
 */
const val SECRETS = "secrets"

fun sharedModule(
    serviceClientConstructor: (SettingsRepository, ErrorMessageBus) -> ServiceClient = ::KtorServiceClient,
) =
    module {
        // The general store stays unqualified so that any consumer resolving
        // `Settings` by type keeps the backed-up store. Only the secrets store
        // is qualified — ask for it on purpose.
        single { provideSettings() }
        single(named(SECRETS)) { provideSecretSettings() }
        single { SettingsRepository(get(), get(named(SECRETS))) }
        singleOf(::NetworkMonitor)
        singleOf(::ErrorMessageBus)
        singleOf(::DeepLinkBus)
        singleOf(::VolumeButtonService)
        singleOf(::ImageCacheInvalidator)
        singleOf(serviceClientConstructor) { bind<ServiceClient>() }
        singleOf(::LogSharer)
        single(createdAtStart = true) {
            ConnectionManager(
                get(),
                get(),
            )
        }
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
        // Sendspin encrypted-protocol identity/trust persistence — the same
        // app settings storage as everything else.
        single<SendspinKeyStore> { SettingsSendspinKeyStore(get()) }
        single { PlayerPositionTracker() }  // Shared live-position source of truth
        single { UserPreferences() }        // Server-synced `auth/me` preferences
        singleOf(::PlayerRequestFactory)    // Pure PlayerAction → Request mapper
        singleOf(::LocalPlayerController)    // Local player: lifecycle + state + commands
        singleOf(::MediaItemFactory)        // Stateless DTO → domain mapper
        singleOf(::PlayerFactory)           // Stateless DTO → domain mapper
        singleOf(::QueueFactory)            // Stateless DTO → domain mapper (depends on MediaItemFactory)
        singleOf(::AiRadioRepository)       // Optional ai_radio plugin: list and run stations
        singleOf(::ServiceClientMediaItemRepository) { bind<MediaItemRepository>() }
        singleOf(::MainDataSource)          // Singleton - held by foreground service
        single(createdAtStart = true) {     // Eager - must observe car edges from launch
            CarDspApplier(get(), get(), get(), get())
        }
        singleOf(::DominantColorViewModel)  // Singleton - app-wide art-color cache
        singleOf(::MdiCodepoints)           // Singleton - MDI name->codepoint table (one-time load)
        viewModelOf(::ThemeViewModel)
        factory { BackgroundRestrictionViewModel(get(), get(), get()) }
        factory { SchemaVersionWarningViewModel(get()) }
        factory { ActionsViewModel(get(), get(), get()) }
        factory { SettingsViewModel(get(), get(), get()) }
        factory { DefaultClickActionsViewModel(get()) }
        factory { CarActionsViewModel(get(), get()) }
        factory { CarDspViewModel(get(), get()) }
        factory { AiRadioViewModel(get(), get()) }
        factory {
            AuthenticationViewModel(
                auth = get(),
                sessionStateFlow = get<ServiceClient>().sessionState,
            )
        }
        factory { LibraryCategoriesViewModel(get(), get()) }
        factory { params -> LibraryListViewModel(params[0], get(), get(), get(), get()) }
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
        factory { params ->
            ArtistDetailsViewModel(params[0], get())
        }
        factory { ViewModeViewModel(get()) }
        factory { params -> ItemListViewModel(params[0], get()) }
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
