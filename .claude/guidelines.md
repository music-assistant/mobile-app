# Development Guidelines

## Code Location Rules

| What | Where |
|------|-------|
| Shared logic | `commonMain/` |
| Android-only | `androidMain/` |
| iOS-only | `iosMain/` |
| Reusable composables | `ui/compose/common/` |
| Feature composables | `ui/compose/{feature}/` |
| ViewModels | Same package as screen |

## Kotlin Conventions

```kotlin
// Prefer StateFlow over LiveData
val state: StateFlow<State> = _state.asStateFlow()

// Use sealed interfaces for navigation/polymorphism
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val message: String) : Result<Nothing>
}

// Minimize expect/actual - keep code in commonMain
```

### Nullability - Use Kotlin Idioms, NOT Java Style

**NEVER write Java-style null checks:**
```kotlin
// ❌ WRONG - Java style
if (player != null) {
    player.play()
}

// ❌ WRONG - verbose
if (dispatcher != null) {
    dispatcher.sendMessage()
} else {
    log("Dispatcher is null")
}
```

**ALWAYS use Kotlin null-safety operators:**
```kotlin
// ✅ CORRECT - safe call
player?.play()

// ✅ CORRECT - safe call with let
player?.let {
    it.play()
    it.updateState()
}

// ✅ CORRECT - let with elvis for else branch
dispatcher?.let {
    it.sendMessage()
    log("Message sent")
} ?: log("Dispatcher is null")

// ✅ CORRECT - elvis operator for default values
val volume = config?.volume ?: 100

// ✅ CORRECT - early return
val player = getPlayer() ?: return
player.play()  // player is smart-cast to non-null

// ✅ CORRECT - elvis with custom action
val token = settings.token.value ?: run {
    log.w { "No token available" }
    return
}
```

**Complex null handling patterns:**
```kotlin
// ✅ Multiple nullables with takeIf/takeUnless
val result = service
    ?.getData()
    ?.takeIf { it.isValid }
    ?.process()
    ?: getDefaultResult()

// ✅ Run block for multiple statements in else
connection?.let {
    it.send(message)
    it.flush()
} ?: run {
    log.e { "Connection is null" }
    reconnect()
}

// ✅ Also for side effects on non-null
dispatcher?.sendHello()?.also {
    log.i { "Hello sent successfully" }
}
```

**Scope functions cheat sheet:**
- `let` - transform nullable to non-null context, returns result
- `run` - execute block and return result (for else branch with elvis)
- `also` - perform side effects, returns original object
- `apply` - configure object, returns object
- `takeIf` / `takeUnless` - conditional filtering

## Compose Conventions

```kotlin
// Inject ViewModels in composables
@Composable
fun Screen(viewModel: ScreenViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
}

// Use remember for expensive computations
val derived = remember(input) { expensiveCalculation(input) }

// Material3 components only
Button(onClick = {}) { Text("Click") }  // ✓
// Not Material2 equivalents
```

## File Organization

- One primary composable per file
- Split by meaning, not by size
- Name file after main composable: `PlayerControls.kt` → `@Composable fun PlayerControls()`
- No previews unless explicitly requested

## Logging

```kotlin
import co.touchlab.kermit.Logger

Logger.withTag("ServiceClient").d { "Connected to $host" }
Logger.withTag("PlayerVM").e(exception) { "Playback failed" }
```

## State Wrappers

`DataState<T>` (`ui/compose/common/DataState.kt`) has `Loading`, `Data(data)`, `Error(error: AppError?)`, `NoData`
and `Stale(data, disconnectedAt, reason)`. `Stale` keeps the last data while the connection is down. Read the
data with `dataOrNull`, which covers `Data` and `Stale`.

## Maintenance Rules

- Update `.claude/dependencies.md` when adding libraries
- Update `.claude/architecture.md` when changing patterns
- Keep `commonMain` as the default location

## Server Connection

Music Assistant server required. Configure:
- Host (IP/hostname)
- Port
- TLS (on/off)
- Auth: login/pass, OAuth, or long-lived token

## Testing Platforms

**Android Auto:**
1. Enable developer mode in Android Auto app
2. Enable "Unknown sources"
3. VPN config: exclude Android Auto from VPN