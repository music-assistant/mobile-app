# Android TV / Google TV Support (Work in Progress)

> **Do not include this file in any PR sent upstream (or anywhere else).** It's a local
> working note for this branch, not project documentation — strip it out (`git rm
> docs/ANDROID-TV.md` or drop the commit that added it) before opening a PR. Maintainers
> generally don't want AI-generated process/decision docs landing in their repo.

This branch (`claude/music-assistant-android-tv-95iu6l`) adds a first pass of Android TV /
Google TV support. It has **not** been built or tested with a real Gradle run — the
environment that authored it had no network access to `dl.google.com` (required to resolve
the Android Gradle Plugin), so every change below needs local verification before it's relied
on or sent upstream.

## Why

The app had zero Android TV awareness: no leanback launcher entry, a hard-required camera
feature that blocks the Play Store listing on TV devices, an activity that force-locks
portrait orientation, and a touch-only Compose UI with no D-pad/keyboard focus handling
anywhere. See the upstream (`music-assistant/mobile-app`) history for context:

- [Issue #689](https://github.com/music-assistant/mobile-app/issues/689) — "Android TV", closed not-planned.
- [Issue #405](https://github.com/music-assistant/mobile-app/issues/405) — "Google TV support", closed completed (status unclear from the manifest/UI as they stand).
- [Discussion #5162](https://github.com/orgs/music-assistant/discussions/5162) — a maintainer confirms the sideloaded app is unreliable on TV today, including a D-pad up/down that hijacks the now-playing volume slider instead of moving player-list focus.

## What changed, commit by commit

1. **`986faae` — Manifest & Play Store TV eligibility**
   `androidApp/src/main/AndroidManifest.xml`: camera feature made optional, added
   `android.software.leanback`/`android.hardware.touchscreen` as optional, added a
   `LEANBACK_LAUNCHER` intent-filter on `MainActivity`, added `android:banner` +
   `res/drawable/banner.xml`.
   Test: `AndroidTvEligibilityTest` (Robolectric, queries `PackageManager` directly).

2. **`3a3961f` — TV orientation lock**
   `MainActivity.kt`: detects `Configuration.UI_MODE_TYPE_TELEVISION` and forces landscape,
   instead of only applying the existing phone-width portrait lock. Logic extracted into a
   pure `resolveOrientationLock()` function.
   Test: `MainActivityOrientationTest` (plain unit test over the extracted function).

3. **`4b8f966` — Hide QR camera scan button with no camera**
   New `hasCamera()` expect/actual (`composeApp/.../utils/CameraAvailability.{kt,android.kt,ios.kt}`),
   used in `SettingsScreen.kt` to hide the WebRTC remote-pairing QR-scan button when there's no
   camera (Android TV). The manual remote-ID text field remains the primary/fallback path.
   Test: `QrScanAvailabilityTest` (Robolectric, toggles `PackageManager.FEATURE_CAMERA_ANY` via
   `Shadows.shadowOf`).

4. **`ddebf9f` — D-pad focus fix in the player switcher**
   `SelectPlayerDialog.kt`: the player list now claims keyboard/D-pad focus (`focusGroup()` +
   `FocusRequester`) as soon as the dialog opens — this is the concrete bug from discussion
   #5162 (D-pad leaking into the volume slider because nothing in the dialog claimed focus).
   Test: `SelectPlayerDialogFocusTest` (Compose UI test, simulates `DirectionDown` /
   `DirectionCenter` key input).

Explicitly **not** done in this pass (candidates for follow-up): nav rail/bar focus order,
playback control focus indication at 10-foot viewing distance, search/library grid D-pad
traversal. None of these blocked basic TV usability the way manifest eligibility and the
player-switcher bug did.

## How to verify locally

```sh
# Full unit/Robolectric suite, or just the new tests:
./gradlew :androidApp:testDebugUnitTest

./gradlew :androidApp:testDebugUnitTest --tests "io.music_assistant.client.feature.AndroidTvEligibilityTest"
./gradlew :androidApp:testDebugUnitTest --tests "io.music_assistant.client.MainActivityOrientationTest"
./gradlew :androidApp:testDebugUnitTest --tests "io.music_assistant.client.feature.QrScanAvailabilityTest"
./gradlew :androidApp:testDebugUnitTest --tests "io.music_assistant.client.ui.compose.home.players.SelectPlayerDialogFocusTest"

# Full debug build, to make sure the manifest/resource changes compile and merge cleanly:
./gradlew :androidApp:assembleDebug
```

Manual QA, on a Google TV emulator (AVD with a "Google TV" system image) or a physical
Chromecast with Google TV:

- [ ] App appears on the TV home screen's app row (banner shows correctly).
- [ ] Navigate home/library/search using only the D-pad.
- [ ] Open the player switcher and change players using D-pad + center/OK only — confirm
      up/down moves between rows and doesn't touch the volume slider behind the dialog.
- [ ] Adjust volume with D-pad without it stealing focus from surrounding lists.
- [ ] Drive playback controls (play/pause/skip) via D-pad + center/OK.
- [ ] Confirm the Now Playing card appears correctly.
- [ ] Confirm the QR-scan button is hidden in Settings → remote/WebRTC connection, and the
      manual remote-ID field still works.

## Once verified

The intent is to send this upstream to `music-assistant/mobile-app` (not just this fork) —
draft issue and PR text are in the conversation this branch came from; ask if you need them
regenerated.
