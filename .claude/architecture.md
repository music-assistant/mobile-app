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

The built-in player functionality uses the Sendspin multi-room audio protocol:

**Lifecycle Management:**
- `MainDataSource` singleton manages Sendspin lifecycle via `SendspinClientFactory`
- Factory pattern for client creation (validates settings, builds config, returns `Result<SendspinClient>`)
- Integration point: `HomeScreenViewModel` interacts with Sendspin via MainDataSource

**Architecture Components:**
- **SendspinClient**: Protocol orchestrator (reduced to ~280 lines after refactoring)
  - Delegates to specialized components following Single Responsibility Principle
- **SendspinClientFactory**: Client creation and validation logic
- **StateReporter**: Periodic state reporting (every 2 seconds) with volume/mute
- **ReconnectionCoordinator**: Recovery management with StreamRecoveryState machine
- **AudioPipeline** (interface): Abstraction for audio playback
  - Implementation: `AudioStreamManager` with multi-threaded architecture
  - Default dispatcher: Decoding (producer)
  - audioDispatcher: Playback (high-priority consumer)
  - Default dispatcher: Adaptation (every 5s)
- **MessageDispatcher**: Protocol state machine with `MessageDispatcherConfig`
- **SendspinError**: Categorized errors (Transient/Permanent/Degraded)

**Connection Modes:**
- **Proxy mode (default)**: Uses main connection (host/port/TLS) + path `/sendspin`
  - Requires authentication with token before protocol handshake
  - Port 8095 by default (same as main API)
- **Custom mode**: Separate host/port configuration
  - Supports direct connection to standalone Sendspin server (port 8927)
  - Auto-detects proxy mode if port matches main connection

**Platform-specific:**
- `MediaPlayerController` has expect/actual for audio output
- `AudioDecoder` has expect/actual for codec handling
  - Android: Decoders output PCM (Concentus for Opus, MediaCodec for FLAC)
  - iOS: Decoders passthrough to MPV (returns original codec)
- Android: Uses `AudioTrack` for low-latency PCM playback
- iOS: Uses MPV (libmpv via MPVKit) for all audio codecs

See `.claude/sendspin-status.md` for complete architecture documentation and `.claude/settings-screen.md` for Settings UI details.

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

**Key Pattern**: Services do NOT create or manage Sendspin directly. They access player data through MainDataSource's playersData StateFlow, maintaining a single source of truth.

See `.claude/sendspin-integration-design.md` and `.claude/sendspin-android-services-integration.md` for detailed technical documentation.

## Misc rules

- Don't ever use non-null assertions in live code (!!). Always handle nulls safely.
- Use Kotlin-like idioms (e.g., prefer `let`, `also`, `apply` for scoping).
- Instead of `if-else` chains, prefer `when` expressions for better readability.
- Instead of `if-else` for nullable variable, use safe calls and the Elvis operator, or `?.let{} ?: run {}` expression.