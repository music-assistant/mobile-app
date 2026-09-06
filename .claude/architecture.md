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