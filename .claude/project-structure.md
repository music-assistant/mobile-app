# Project Structure

## Modules

| Module | Content |
|--------|---------|
| `composeApp` | Shared app code (Compose Multiplatform) |
| `androidApp` | Android application: `MainActivity`, media services, Android Auto (`auto/`, `services/`) |
| `iosApp` | Xcode project: SwiftUI host, CarPlay, Siri, Now Playing, native audio output |
| `shared-icons` | Icon sets |
| `nav` | Helpers on top of Navigation3 |
| `ui-utils` | Compose UI helpers, for example `DeviceChrome` |
| `sendspin-kmp` | Sendspin player library, published to GitHub Packages. Run its tests with `./gradlew :sendspin-kmp:testAndroidHostTest`. See `sendspin-kmp/README.md`. |

## Source Sets

```
composeApp/src/
├── commonMain/      # Most of the code
├── androidMain/     # Android implementations (settings, discovery, image loader, logging, player ports)
├── iosMain/         # iOS implementations, KmpHelper bridge, CarPlay helpers, MainViewController
├── commonTest/
├── androidHostTest/ # Run with :composeApp:testAndroidHostTest
└── iosTest/
```

## Package Organization

```
commonMain/kotlin/io/music_assistant/client/
├── api/              # ServiceClient, KtorServiceClient, SessionMachine, transports, Request, DeepLinkBus
├── auth/             # AuthenticationManager, OAuth, PKCE
├── connection/       # ConnectionManager
├── data/
│   ├── model/
│   │   ├── server/   # Server DTOs (raw API responses)
│   │   └── client/   # Domain models (Player, Queue, AppMediaItem)
│   ├── repository/   # Repositories
│   ├── play/         # PlayDispatcher, PlayRequest, PlayMode
│   ├── announcement/ # AnnouncementRepository
│   └── MainDataSource.kt, LocalPlayerAdapter.kt, LocalPlayerEndpoints.kt
├── di/               # Koin modules (SharedModule.kt)
├── discovery/        # mDNS server discovery
├── imageloader/      # ArtworkRepository and the Coil adapter
├── input/            # VolumeButtonService
├── logging/          # In-app log buffer and log sharing
├── settings/         # SettingsRepository and setting types
├── webrtc/           # Signaling, peer connection, data channels, HTTP proxy
├── ui/
│   ├── compose/
│   │   ├── common/   # Reusable composables
│   │   ├── home/     # HomeScreen, HomeScreenViewModel, MainNavRoot, players pager
│   │   ├── library/  # Library lists
│   │   ├── item/     # Item details
│   │   ├── search/
│   │   ├── settings/
│   │   └── auth/, grid/, nav/, party/, provider/
│   │   TopLevelNavRoot.kt, App.kt
│   └── theme/        # Material3 theme, AccentColor
└── utils/            # Extensions and helpers
```

## Feature Pattern

```
ui/compose/{feature}/
├── {Feature}Screen.kt      # Main composable
├── {Feature}ViewModel.kt   # State + logic
└── {Component}.kt          # Feature-specific components
```

## Platform-Specific Code

| Feature | Android | iOS |
|---------|---------|-----|
| Local Player | Sendspin (`:sendspin-kmp`), `AudioTrackSink` | Sendspin, `AudioQueueSink` → `NativeAudioController.swift` |
| Media session | `MainMediaPlaybackService`, `SharedMediaSessionManager` | `NowPlayingCoordinator.swift` |
| Car Integration | Android Auto (`AndroidAutoPlaybackService`, `AutoLibrary`) | CarPlay |
| Server discovery | `NsdManager` | `MassServiceBrowser.swift` (`NWBrowser`) |
| Settings Storage | SharedPreferences | NSUserDefaults |

## Key Files

- `api/ServiceClient.kt` - Interface of the MA connection. `api/KtorServiceClient.kt` implements it.
- `api/SessionMachine.kt` - Connection lifecycle
- `data/MainDataSource.kt` - Central state management
- `di/SharedModule.kt` - Common DI definitions
- `ui/compose/TopLevelNavRoot.kt`, `ui/compose/home/MainNavRoot.kt` - Navigation destinations

## iOS Files

```
iosApp/iosApp/
├── CarPlay/
│   ├── CarPlaySceneDelegate.swift                     # Scene delegate, templates
│   ├── CarPlayNavigationCoordinator.swift             # Template navigation
│   ├── CarPlayInterfaceControllerNavigationDriver.swift
│   ├── CarPlayContentManager.swift                    # Data fetching, AppMediaItem → CPListItem
│   └── SiriIntentHandler.swift                        # Siri media intents
├── CarPlay.entitlements, NoCarPlay.entitlements       # carplay-audio, Siri, associated domains
├── iOSApp.swift                                       # App entry, AppDelegate, deep links
├── ContentView.swift                                  # Compose host, DeviceChrome probe
└── Info.plist                                         # Scenes, URL scheme
iosApp/                                                # NativeAudioController, AudioDecoders, NowPlayingCoordinator

composeApp/src/iosMain/
└── kotlin/.../di/KmpHelper.kt                         # iOS bridge (audiobooks, radio, search, artwork, deep links)
```

See `.claude/carplay.md` for full documentation.
