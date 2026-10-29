package io.music_assistant.client.di

import io.music_assistant.client.api.DeepLinkBus
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.KtorServiceClient
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.connection.ConnectionManager
import io.music_assistant.client.data.CarDspApplier
import io.music_assistant.client.data.LocalPlayerAdapter
import io.music_assistant.client.data.LocalPlayerEndpoints
import io.music_assistant.client.data.LocalPlayerFactory
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.PlayerPositionTracker
import io.music_assistant.client.data.PlayerRequestFactory
import io.music_assistant.client.data.SendspinLocalPlayerFactory
import io.music_assistant.client.data.UserPreferences
import io.music_assistant.client.data.announcement.AnnouncementRepository
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.repository.AiRadioRepository
import io.music_assistant.client.data.repository.ItemRefresher
import io.music_assistant.client.data.repository.LibrarySortRepository
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.QueueOverlayRepository
import io.music_assistant.client.data.repository.ServiceClientMediaItemRepository
import io.music_assistant.client.data.repository.TranscriptRepository
import io.music_assistant.client.imageloader.ArtworkDiskStore
import io.music_assistant.client.imageloader.ArtworkRepository
import io.music_assistant.client.imageloader.ArtworkTransport
import io.music_assistant.client.imageloader.KtorArtworkTransport
import io.music_assistant.client.imageloader.buildArtworkDiskCache
import io.music_assistant.client.imageloader.toArtworkLoaderPlatformContext
import io.music_assistant.client.input.VolumeButtonService
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.player.MediaSessionBridge
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.SettingsSendspinKeyStore
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
import io.music_assistant.client.ui.compose.home.players.QueueDjViewModel
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.item.ItemListViewModel
import io.music_assistant.client.ui.compose.item.ViewModeViewModel
import io.music_assistant.client.ui.compose.item.artist.ArtistDetailsViewModel
import io.music_assistant.client.ui.compose.library.AiRadioViewModel
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryListViewModel
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.ui.compose.settings.CarActionsViewModel
import io.music_assistant.client.ui.compose.settings.CarDspViewModel
import io.music_assistant.client.ui.compose.settings.DefaultClickActionsViewModel
import io.music_assistant.client.ui.compose.settings.SendspinSettingsViewModel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.LocalNetworkPermissionGate
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.appVersion
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.sendspin.api.SendspinKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        single { appVersion(get()) }        // Singleton - fixed for the process lifetime
        singleOf(::LocalNetworkPermissionGate)
        singleOf(::ErrorMessageBus)
        singleOf(::DeepLinkBus)
        singleOf(::VolumeButtonService)
        single<ArtworkDiskStore> {
            ArtworkDiskStore(
                cache = buildArtworkDiskCache(toArtworkLoaderPlatformContext(get<PlatformContext>())),
            )
        }
        single<ArtworkTransport> {
            KtorArtworkTransport(
                httpClient = get(named("webrtcHttpClient")),
                serviceClient = get(),
            )
        }
        single<ArtworkRepository> {
            ArtworkRepository(
                store = get(),
                transport = get(),
                serviceClient = get(),
                now = { currentTimeMillis() },
            )
        }
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
        single { MediaSessionBridge() }
        // Sendspin encrypted-protocol identity/trust persistence — the same
        // app settings storage as everything else.
        single<SendspinKeyStore> { SettingsSendspinKeyStore(get()) }
        single { PlayerPositionTracker() }  // Shared live-position source of truth
        single { UserPreferences(get()) }   // Server-synced `auth/me` preferences
        singleOf(::PlayerRequestFactory)    // Pure PlayerAction → Request mapper
        single { LocalPlayerEndpoints(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        single<LocalPlayerFactory> { SendspinLocalPlayerFactory(get(), get(), get(), get(), get(), get()) }
        singleOf(::LocalPlayerAdapter)      // Local player: app side of the Sendspin module
        singleOf(::MediaItemFactory)        // Stateless DTO → domain mapper
        singleOf(::PlayerFactory)           // Stateless DTO → domain mapper
        singleOf(::QueueFactory)            // Stateless DTO → domain mapper (depends on MediaItemFactory)
        single { AiRadioRepository(get(), get()) } // Optional ai_radio plugin: stations and queue DJ
        single {                            // Typed and spoken announcements; outlives the dialog
            AnnouncementRepository(
                apiClient = get(),
                httpClient = get(named("webrtcHttpClient")),
                microphone = get(),
                errorBus = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single {                            // Sole owner of the `providers` list
            ProviderRepository(
                apiClient = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single {                            // Queue ambient sound overlay; Apply outlives the dialog
            QueueOverlayRepository(
                apiClient = get(),
                providerRepository = get(),
                mediaItemFactory = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single {                            // Sole play_media sender; requests outlive the screen
            PlayDispatcher(
                apiClient = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single<MediaItemRepository> { ServiceClientMediaItemRepository(get(), get()) }
        single {                            // Sole refresh_item sender; refreshes outlive the page
            ItemRefresher(
                mediaItemRepository = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single {                            // Transcripts load once for the episode page and the player
            TranscriptRepository(
                mediaItemRepository = get(),
                apiClient = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        singleOf(::LibrarySortRepository)   // Library sort choices; caches the 2.11 sort_options
        singleOf(::MainDataSource)          // Singleton - held by foreground service
        single(createdAtStart = true) {     // Eager - must observe car edges from launch
            CarDspApplier(get(), get(), get(), get())
        }
        singleOf(::DominantColorViewModel)  // Singleton - app-wide art-color cache
        singleOf(::MdiCodepoints)           // Singleton - MDI name->codepoint table (one-time load)
        viewModelOf(::ThemeViewModel)
        factory { BackgroundRestrictionViewModel(get(), get(), get()) }
        factory { SchemaVersionWarningViewModel(get()) }
        factory { ActionsViewModel(get(), get(), get(), get()) }
        factory { SettingsViewModel(get(), get(), get(), get(), get()) }
        factory {
            val mainDataSource = get<MainDataSource>()
            SendspinSettingsViewModel(
                settings = get(),
                isLocalPlayerPlaying = { mainDataSource.localPlayer.value?.player?.isPlaying == true },
            )
        }
        factory { DefaultClickActionsViewModel(get()) }
        factory { CarActionsViewModel(get(), get()) }
        factory { CarDspViewModel(get(), get()) }
        factory { AiRadioViewModel(get(), get(), get()) }
        factory {
            AuthenticationViewModel(
                auth = get(),
                sessionStateFlow = get<ServiceClient>().sessionState,
            )
        }
        factory { LibraryCategoriesViewModel(get(), get()) }
        factory { params -> LibraryListViewModel(params[0], get(), get(), get(), get(), get(), get(), get()) }
        factory { params -> BrowseViewModel(params.getOrNull<String>(), get(), get(), get()) }
        factory { params ->
            ItemDetailsViewModel(
                get(),
                get(),
                get(),
                get(),
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
            ArtistDetailsViewModel(params[0], get(), get())
        }
        factory { ViewModeViewModel(get()) }
        factory { params -> ItemListViewModel(params[0], get(), get()) }
        factory { DspSettingsViewModel(get()) }
        factory { QueueDjViewModel(get(), get<MainDataSource>().aiRadioQueueDjAvailable) }
        factory { HomeScreenViewModel(get(), get(), get(), get(), get(), get()) }
        factory { SearchViewModel(get(), get(), get(), get(), get()) }
        factory { ProviderViewModel(get(), get()) }
    }

/**
 * Cleanup function to properly close all singleton resources.
 * Call this before stopKoin() to ensure proper resource cleanup.
 */
fun cleanupSingletons() {
    // Cleanup is handled by individual components' lifecycle
}
