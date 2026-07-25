# Testing Conventions

Tests live in `androidApp/src/test/kotlin/` even though most app code is in
`composeApp/src/commonMain/` — Compose UI tests run through the Android target via Robolectric.

## Test types

- **Feature tests** (`androidApp/.../feature/*Test.kt`): end-to-end, drive the real `App()`
  composable through a page-object layer (`support/pages/*Page.kt`). Use
  `launchApp(composeTestRule)` / `launchLoggedInApp(composeTestRule, fakeServiceClient)` from
  `support/LaunchApp.kt` to get to a `ConnectPage`/`HomePage`.
- **Component tests** (`androidApp/.../ui/compose/**/*Test.kt`): render a single composable
  directly via `composeTestRule.setContent { ... }`, no page objects. If the composable needs
  DI (e.g. `MdiCodepoints` for icon lookups), wrap it in a `KoinApplication { ... }` with just
  the modules it needs — see `SelectPlayerDialogTest.kt`.
- **Regression tests** (`androidApp/.../regression/*Test.kt`): named for the specific bug they
  guard against.
- **Pure unit tests** (no Compose, no Robolectric-specific APIs): still annotate
  `@RunWith(AndroidJUnit4::class)` for consistency and because constructing framework classes
  like `android.content.res.Configuration` requires Robolectric's shadow environment even when
  you're only touching plain fields.

## Harness

- `createTestRuleChain()` (`support/rules/TestRuleChain.kt`) wires Koin
  (`createKoinTestRule()`, real modules + `FakeServiceClient` swapped in for the network
  client) and zeroes UI debounce timings (`TestStateRule`).
- `Qualifiers.kt` holds reusable Robolectric `@Config(qualifiers = ...)` strings (e.g.
  `MEDIUM_PHONE`). Add more here as needed — Robolectric supports the `television` UI-mode
  resource qualifier (e.g. `"television-w1280dp-h720dp"`) for TV-shaped configs.
- `Res.string.xxx.get()` (`support/StringResourceExt.kt`) resolves a Compose Multiplatform
  string resource synchronously for assertions.

## Useful Robolectric shadows

- `org.robolectric.Shadows.shadowOf(packageManager).setSystemFeature(name, available)` —
  toggle `PackageManager.hasSystemFeature()` results (e.g. camera) in a test; Robolectric's
  default is `false` for anything not explicitly set.
- `packageManager.queryIntentActivities(...)` and
  `packageManager.getPackageInfo(pkg, PackageManager.GET_CONFIGURATIONS).reqFeatures` make
  real manifest-level assertions (intent-filters, `<uses-feature required=...>`) possible
  against the actual merged `AndroidManifest.xml`, since
  `testOptions.unitTests.isIncludeAndroidResources = true` is set in
  `androidApp/build.gradle.kts`.

## Keyboard / D-pad input

`composeTestRule.onNodeWithTag(...).performKeyInput { pressKey(Key.DirectionDown);
pressKey(Key.DirectionCenter) }` simulates remote/keyboard navigation — used for Android TV
D-pad focus tests (see `SelectPlayerDialogFocusTest`).

## Environment limitation (Claude Code cloud/remote sessions)

Some sandboxed/remote execution environments have no network access to `dl.google.com`,
which the Android Gradle Plugin needs to resolve. If `./gradlew` fails on plugin resolution
(a 403 through the proxy, not a normal dependency error), Android builds/tests simply cannot
run in that session — don't keep retrying. Verify via a local checkout instead, or via CI:
`check-pr-android.yml` / `check-pr-lint.yml` trigger on `pull_request` to `main`, not on a
plain branch push, so a draft PR is the fastest way to get a real build/test run.
