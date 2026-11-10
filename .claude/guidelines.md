# Development Guidelines

## Code Location Rules

| What | Where |
|------|-------|
| Shared logic | `commonMain/` |
| Android-only | `androidMain/` |
| iOS-only | `iosMain/` |
| Reusable composables | `ui/common/composables/` |
| Feature composables | `ui/{feature}/composables/` |
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

```kotlin
sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Data<T>(val value: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
    data object NoData : DataState<Nothing>
}
```

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
## Pull Requests

- Always write the PR description with `.github/PULL_REQUEST_TEMPLATE.md`. Keep its headings, its
  comment and its checklist, and fill in `Closes #` with the issue (remove the line if there is none).
- Put the summary of the change under `Closes #`, and answer each template question under its
  heading. Write "No." if there is nothing to add.
- Tick a checklist item only if it is true. Tick the checks item only after
  `./gradlew detektAll testAndroidHostTest :androidApp:testDebug` passed for this branch.
- When you edit an open PR, keep the template in the new description.
