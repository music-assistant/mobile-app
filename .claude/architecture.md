# Architecture Patterns

## MVVM + Unidirectional Data Flow

```
User Action → ViewModel → Update State → UI Recomposition
                ↓
         Repository/DataSource
                ↓
           Network/Storage
```

- ViewModels expose `StateFlow<T>` for reactive UI state
- UI collects state with `collectAsStateWithLifecycle()`
- User actions invoke ViewModel methods
- Wrap async results in `DataState<T>` (Loading/Data/Error/NoData)

## Dependency Injection (Koin)

```kotlin
// Module definition
val appModule = module {
    singleOf(::Repository)
    viewModelOf(::FeatureViewModel)
}

// Usage in Composable
val viewModel = koinViewModel<FeatureViewModel>()
```

## Navigation (Navigation3)

- Type-safe routes via `@Serializable` data classes/objects
- Sealed interface for destination grouping
- Modal sheets for overlays

```kotlin
@Serializable sealed interface NavScreen {
    @Serializable data object Home : NavScreen
    @Serializable data class Detail(val id: String) : NavScreen
}
```

## Expect/Actual Pattern

Use sparingly. Most code stays in `commonMain`.

```kotlin
// commonMain
expect class PlatformFeature {
    fun doThing()
}

// androidMain
actual class PlatformFeature {
    actual fun doThing() { /* Android impl */ }
}
```

## Data Layer

- **Repository**: Single source of truth, exposes StateFlows
- **DataSource**: Network/local data access
- **Models**: Server DTOs in `model/server/`, domain models in `model/client/`
- **List payloads**: `library_items` and `player_queues/items` answer in one message and the
  server caps a message at about 9 MB. Never send a huge `limit`. Page with `Request.fetchAllPages`
  (`api/Paging.kt`) when a caller needs the whole list. `playlist_tracks` is streamed by the
  server in 500-item `partial` batches that `RpcEngine` reassembles. `library_items` already
  returns slim summary items by default.
- **In-list filter**: `List.clientFiltered(query)` (`model/client/QueryFilter.kt`) filters loaded
  items on the client. Derive the visible list as raw → filter → sort in one place.

## HTTP Clients

- Get every `HttpClient` from the Koin `HttpClientFactory`. Do not call `HttpClient(engine)` directly.
- Android (`AndroidHttpClientFactory`): OkHttp with the KeyChain client certificate that the user selected
  (`SettingsRepository.clientCertificateAlias`), for mTLS. Trust stays the platform default, so the network
  security config applies. All clients share one connection pool. A certificate change evicts that pool.
- iOS (`IosHttpClientFactory`): Darwin. `handleChallenge` answers a client-certificate challenge with the identity
  in `KeychainClientIdentity`, only while `clientCertificateAlias` is set. The Darwin engine owns its session pool,
  so `KtorServiceClient` makes a new client when the certificate changes.

## Artwork Loading

- **`ArtworkRepository`**: Single owner of artwork fetching, disk caching, freshness, and concurrent-request deduplication. Shared by all platforms.
- **Compose and Android media**: Use the singleton Coil loader. Its artwork adapter resolves through the repository; Coil owns decoding and decoded-memory caching, not a second disk cache.
- **CarPlay and iOS Now Playing**: Use `KmpHelper.loadArtwork` through the shared `NativeArtworkLoader` adapter. Kotlin owns one cancellable coroutine covering fresh-token lookup and body loading; it accepts a synchronous Swift cache probe after token resolution, so a versioned native decoded-image hit skips the body entirely. Swift owns the bounded shared `NSCache` (including decode and presentation); only reusable repository results are inserted under the token identity-plus-digest key.
- **WebRTC**: Uses the same repository through the existing HTTP proxy. Cache identities include the server ID so synthetic URLs cannot collide across servers.
- **Cache identity**: Decoded-image keys include the content version. Decode-failure eviction targets only that version, never a newer replacement.

**Key rule**: Route new artwork consumers through the shared loader or bridge. Do not add independent downloads, disk caches, or URL-only caches that bypass repository freshness.

## Compose Guidelines

- Material3 components
- Extract reusable composables to `ui/common/composables/`
- Use `remember`/`derivedStateOf` for computed values
- Split large composables into smaller files by meaning
- Previews only when explicitly requested

## State Management

```kotlin
// ViewModel
class FeatureViewModel : ViewModel() {
    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()
    
    fun onAction(action: Action) {
        // Update state
    }
}

// Composable
@Composable
fun FeatureScreen(viewModel: FeatureViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Render UI
}
```

## Error Handling

- Use sealed class/interface for result types
- Display errors via Toast or inline error states
- Log with context: `Logger.withTag("Component").e { "message" }`

## UI Architecture

**IMPORTANT**: The project is transitioning from the old MainScreen/MainViewModel to the new HomeScreen/HomeScreenViewModel architecture.

- **Deprecated (do NOT use)**: `MainScreen.kt`, `MainViewModel.kt`
- **Current (use these)**: `HomeScreen.kt`, `HomeScreenViewModel.kt`

When implementing new features or integrations:
- Use `HomeScreenViewModel` as reference for architecture patterns
- Add dependencies to `HomeScreenViewModel` in `SharedModule.kt`
- Do NOT modify `MainViewModel` - it's legacy code being phased out

### Sendspin Integration

The built-in player is the `:sendspin` Gradle module (`io.music_assistant.sendspin`). The module is pure Kotlin Multiplatform. It has no Compose and no Koin dependency, and it is fully unit-tested with fakes.

**Public surface** (package `api`):
- `SendspinPlayer(config: StateFlow<LocalPlayerConfig?>, deps, scope)` creates the player. A `null` config disables it. There is no start or stop call.
- `state: StateFlow<PlayerState>` is `Disabled`, `Connecting`, `Connected`, `Reconnecting`, or `Failed`. `Connected` carries the player id, the server name, the clock quality, and the audio status.
- `events: Flow<PlayerEvent>` carries `PlaybackStarted`, `PlaybackStopped(cause)`, `ServerRefreshNeeded`, `FocusRegained`, and `Warning(code)`.
- Ports the app implements: `AudioSink`, `DecoderFactory`, `SendspinKeyStore`, and `Endpoint.WebRtc.openChannel`.
- The app depends on `sendspin.api` and the root factory only. Every other declaration in the module is `internal`, so the compiler rejects a new leak. The Noise primitives are a private default of the composition root, not an app port.

**Internal layout**: `wire` (one parse per message), `transport` (one connection, no reconnect), `session` (Noise session on the caller's coroutine), `connection` (the single reconnect policy and the liveness watchdog), `clock` (seeded Kalman filter over probe bursts), `audio` (byte-capped jitter buffer, scheduler, drift corrector), `player` (composition root). The packages `noise`, `identity`, `pairing`, and `management` are unchanged from the previous implementation.

**App side** (`composeApp`):
- `data/LocalPlayerAdapter` owns the player and everything about the MA player model: optimistic UI, the offline command queue, and server event reconciliation.
- `data/LocalPlayerEndpoints` derives the endpoint from the MA session. A WebRTC session gets the data channel. A direct session gets the proxied WebSocket at `<wsUrl>/sendspin`, or the custom Sendspin server from settings. A reconnecting MA session keeps the last endpoint.
- `player/local/` holds the platform sinks and decoders. Android: `AudioTrackSink` and `AndroidDecoderFactory`. iOS: `AudioQueueSink` and pass-through `IosDecoderFactory` (the native player decodes).
- The control plane stays on the MA REST API. Sendspin carries audio only (role `player@v1`).
- Only the Noise-encrypted protocol is supported. Servers that predate it cannot use the local player.

### Android Services Integration

Android foreground services integrate with Sendspin through MainDataSource:

**MainMediaPlaybackService**:
- Handles notifications and lock screen controls
- Shows all active players (excluding deprecated builtin players)
- Accesses player state via `MainDataSource.playersData`
- Uses `MediaSessionHelper` for MediaSession management and volume control (see `.claude/volume-control.md`)

**AndroidAutoPlaybackService**:
- Provides Android Auto support via `MediaBrowserServiceCompat`
- Shows first player with active playback (`queueInfo?.currentItem != null`)
- Uses `playerData.queue` for queue access (not deprecated `builtinPlayerQueue`)
- When Sendspin is playing locally, it appears in Android Auto
- Supports library browsing via `AutoLibrary`
- Shows a Home tab that copies the app home page. The tab has one browsable item for each
  recommendation row. `visibleHomeFolders` (`ui/compose/home/HomeRowsConfig.kt`) filters and
  orders the rows. It uses the same rules and the same `homeRowsConfig` as the app. The Shortcuts
  row is not in the tab. The user turns the tab on or off in Settings → Car → Tabs.
- All actions go through `MainDataSource.playerAction()` and `queueAction()`
- Publishes browse-row and queue-row artwork as opaque, read-only `content://` URIs through
  `AndroidAutoArtworkProvider`. A media host fetches icon URIs in its own process and its own UID,
  so a raw server URL fails whenever the app has routing the host lacks (split-tunnel VPN) and
  always fails for `mawebrtc://` URLs. The provider decodes an authenticated token, fetches under
  the Music Assistant UID, and streams a bounded JPEG. `onGetRoot` hands a prefix read grant to a
  caller whose package really owns its UID and that `MediaSessionManager` trusts for media control.
  Now-playing artwork is unaffected: it is already an in-process bitmap in the session metadata.

**Key Pattern**: Services do NOT create or manage Sendspin directly. They access player data through MainDataSource's playersData StateFlow, maintaining a single source of truth.

See `.claude/sendspin-integration-design.md` and `.claude/sendspin-android-services-integration.md` for detailed technical documentation.

## Misc rules

- Don't ever use non-null assertions in live code (!!). Always handle nulls safely.
- Use Kotlin-like idioms (e.g., prefer `let`, `also`, `apply` for scoping).
- Instead of `if-else` chains, prefer `when` expressions for better readability.
- Instead of `if-else` for nullable variable, use safe calls and the Elvis operator, or `?.let{} ?: run {}` expression.