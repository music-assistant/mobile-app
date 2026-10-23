# Project Structure

## Modules

| Module | Content |
|--------|---------|
| `composeApp` | Shared app code (Compose Multiplatform) |
| `androidApp` | Android application |
| `shared-icons` | Icon sets |
| `sendspin-kmp` | Sendspin player library, published to GitHub Packages. Run its tests with `./gradlew :sendspin-kmp:testAndroidHostTest`. See `sendspin-kmp/README.md`. |

## Source Sets

```
composeApp/src/
├── commonMain/     # 95%+ of code lives here
├── androidMain/    # ExoPlayer, MediaService, Android Auto
├── iosMain/        # AVPlayer, CarPlay
└── appleMain/      # Shared Apple code (iOS + macOS)
```

## Package Organization

```
commonMain/kotlin/
├── api/              # WebSocket client, API interfaces
├── data/
│   ├── model/
│   │   ├── server/   # Server DTOs (raw API responses)
│   │   └── client/   # Domain models (Player, Queue, AppMediaItem)
│   └── repository/   # Data sources, repositories
├── di/               # Koin modules
├── ui/
│   ├── common/
│   │   └── composables/  # Reusable UI components
│   ├── home/             # Home screen + HomeViewModel
│   │   └── composables/  # Home-specific components
│   ├── library/          # Library browser
│   ├── player/           # Player controls
│   ├── queue/            # Queue management
│   └── settings/         # Settings screen
├── utils/            # Navigation, extensions, helpers
└── theme/            # Material3 theme, colors
```

## Feature Module Pattern

```
ui/{feature}/
├── {Feature}Screen.kt      # Main composable
├── {Feature}ViewModel.kt   # State + logic
├── {Feature}State.kt       # State data class (if complex)
└── composables/            # Feature-specific components
    ├── {Component}A.kt
    └── {Component}B.kt
```

## Platform-Specific Code

| Feature | Android | iOS |
|---------|---------|-----|
| Local Player | ExoPlayer (Media3) | AVPlayer |
| Background Playback | MediaService | - |
| Car Integration | Android Auto | CarPlay |
| Settings Storage | SharedPreferences | NSUserDefaults |

## Key Files

- `api/ServiceClient.kt` - WebSocket connection to MA server
- `data/repository/MainDataSource.kt` - Central state management
- `di/SharedModule.kt` - Common DI definitions
- `utils/Navigation.kt` - Navigation destinations

## iOS CarPlay Files

```
iosApp/iosApp/
├── CarPlay/
│   ├── CarPlaySceneDelegate.swift    # Scene delegate, templates, navigation
│   └── CarPlayContentManager.swift   # Data fetching, AppMediaItem → CPListItem
├── CarPlay.entitlements              # carplay-audio entitlement
├── iOSApp.swift                      # AppDelegate for scene routing
└── Info.plist                        # CarPlay scene configuration

composeApp/src/iosMain/
└── kotlin/.../di/KmpHelper.kt        # iOS bridge (audiobooks, radio, search)
```

See `.claude/carplay.md` for full documentation.