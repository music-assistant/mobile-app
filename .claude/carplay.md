# CarPlay Integration

**Last reviewed:** 2026-04-28

## Architecture

CarPlay uses a single `CPListTemplate` as root (not `CPTabBarTemplate`) with two sections:

```
Root: CPListTemplate ("Library")
├── Section 1: Browse → CPGridTemplate (7 categories)
│   ├── Artists    (mic.fill)                        → push CPListTemplate
│   ├── Albums     (opticaldisc.fill)                → push CPListTemplate
│   ├── Tracks     (music.note)                      → push CPListTemplate
│   ├── Playlists  (list.bullet.rectangle.fill)      → push CPListTemplate
│   ├── Audiobooks (book.fill)                       → push CPListTemplate
│   ├── Podcasts   (antenna.radiowaves.left.and.right) → push CPListTemplate
│   └── Radio      (radio.fill)                      → push CPListTemplate
└── Section 2: Recommendations (CPListImageRowItem, async loaded)
    └── RecommendationFolders with artwork thumbnails (up to 8 per folder)
```

### Template Constraints (iOS 18+)

- `CPTabBarTemplate` only accepts `CPListTemplate` and `CPGridTemplate` — NOT `CPSearchTemplate`
- `CPSearchTemplate` cannot be pushed onto navigation stack either
- Only these templates can be pushed: `CPListTemplate`, `CPGridTemplate`, `CPNowPlayingTemplate`, `CPAlertTemplate`, `CPActionSheetTemplate`, `CPVoiceControlTemplate`
- Search is available via Siri voice commands for CarPlay audio apps

### Browse Icons

Browse uses a `CPGridTemplate` with SF Symbol icons and adaptive theming:
- **Dark mode**: Background `#404378` (primaryContainer), icon tint `#C0C1FF` (primary)
- **Light mode**: Background `#E0DEFF`, icon tint `#575992`
- Icons centered and scaled to 80% of available space
- Uses `UIImageAsset` for automatic dark/light switching

### Artwork Loading

`CarPlayImageLoader` singleton handles all artwork:
- `NSCache`-backed image caching
- Async URL loading for category items and recommendation folders
- Fallback to SF Symbol placeholders while loading
- Images updated in-place on `CPListItem` once downloaded

## Files

| File | Purpose |
|------|---------|
| `iosApp/iosApp/CarPlay/CarPlaySceneDelegate.swift` | Scene delegate, template creation, navigation |
| `iosApp/iosApp/CarPlay/CarPlayContentManager.swift` | Data fetching bridge, maps `AppMediaItem` → `CPListItem`, image loading |
| `iosApp/iosApp/CarPlay/SiriIntentHandler.swift` | `INPlayMediaIntentHandling` — resolve search + handle playback via Siri |
| `iosApp/iosApp/iOSApp.swift` | `AppDelegate` for CarPlay scene routing; calls `bootstrapKmp()` from `iOSApp.init()` before any scene connects |
| `iosApp/NowPlayingManager.swift` | Control Center / lock screen / Now Playing artwork via `MPNowPlayingInfoCenter` |
| `composeApp/src/iosMain/.../MainViewController.kt` | Hosts `bootstrapKmp()` (idempotent KMP/Koin init) and `MainViewController()` for the SwiftUI Compose bridge |
| `composeApp/src/iosMain/.../KmpHelper.kt` | iOS-only bridge: fetch methods for all categories, recommendations, search, playback |
| `iosApp/iosApp/CarPlay.entitlements` | `com.apple.developer.carplay-audio` entitlement |
| `iosApp/iosApp/Info.plist` | `CPTemplateApplicationSceneSessionRoleApplication` scene config |

## KmpHelper Bridge Methods

Swift-accessible methods in `KmpHelper.kt` (iosMain only):

| Method | MA Request |
|--------|-----------|
| `fetchArtists()` | `Request.Artist.listLibrary()` |
| `fetchAlbums()` | `Request.Album.listLibrary()` |
| `fetchTracks()` | `Request.Track.list()` |
| `fetchPlaylists()` | `Request.Playlist.listLibrary()` |
| `fetchAudiobooks()` | `Request.Audiobook.listLibrary()` |
| `fetchPodcasts()` | `Request.Podcast.listLibrary()` |
| `fetchRadioStations()` | `Request.RadioStation.listLibrary()` |
| `fetchRecommendations()` | `Request.Library.recommendations()` |
| `fetchRecommendationFolders()` | Filters `AppMediaItem.RecommendationFolder` |
| `search()` | Searches 6 media types, limit 10 |
| `playMediaItem()` | Uses selected player + `QueueOption.PLAY` |

## Koin Initialization Timing

A cold launch from CarPlay (tapping the icon on the head unit) connects only the `CPTemplateApplicationScene` — SwiftUI's `WindowGroup` scene never connects, so `ContentView.onAppear` never fires. Anything tied to the SwiftUI lifecycle is therefore unreachable on this path.

`iOSApp.init()` calls `MainViewControllerKt.bootstrapKmp()` before any scene-delegate `didConnect` runs. `bootstrapKmp()` is idempotent — backed by a `Unit by lazy` because `startKoin` (inside `initKoin`) throws if invoked twice — so `MainViewController()`'s `ComposeUIViewController` configure block can still call it on the SwiftUI-only path without conflict.

`KmpState.isReady` and `KMPReadyNotification` remain in `iOSApp.swift` for `SiriIntentHandler`, which can be invoked outside the scene lifecycle.

## Item Selection Flow

1. User taps Browse → pushes `CPGridTemplate` with 7 category buttons
2. User taps a category → pushes `CPListTemplate` with items from `CarPlayContentManager`
3. User taps an item → `CarPlayContentManager.playItem()` → `KmpHelper.playMediaItem()`
4. `CPNowPlayingTemplate.shared` is pushed for playback controls
5. `NowPlayingManager` updates `MPNowPlayingInfoCenter` (artwork, metadata, remote commands)

## Adding New Categories

1. Add fetch method to `KmpHelper.kt` (iosMain only)
2. Add corresponding method to `CarPlayContentManager.swift`
3. Add entry to grid buttons and handler in `CarPlaySceneDelegate.swift`

## Implementation Status

- [x] Root library template with Browse + Recommendations sections
- [x] Browse grid with 7 categories (Artists, Albums, Tracks, Playlists, Audiobooks, Podcasts, Radio)
- [x] Adaptive dark/light mode icons
- [x] Async artwork loading with caching for category lists
- [x] Recommendation folders as `CPListImageRowItem` with artwork thumbnails
- [x] Item playback via KmpHelper bridge
- [x] Now Playing template integration
- [x] Control Center / lock screen artwork via NowPlayingManager
- [x] Koin initialization timing guard
- [x] Search via KmpHelper (Siri-driven)
- [x] Cold-launch black screen on head unit (#277, 2026-04-26) — fixed; CarPlay loads reliably from a cold tap on the head unit

## TODO

- [ ] Siri search not triggering — `SiriIntentHandler.swift` implements `INPlayMediaIntentHandling` (both `resolveMediaItems` and `handle`); `Info.plist` declares `INPlayMediaIntent` and `NSSiriUsageDescription`. But Siri still does not show the app as a media option, and **no `INInteraction.donate()` or `INMediaAffinityIntent` calls exist anywhere in `iosApp/`** (verified 2026-04-28). Donation is needed so Siri can learn what the user plays through the app. Likely also need: Siri capability enabled in Xcode Signing & Capabilities. Test on physical device (Siri intents may not fully work on simulator).
