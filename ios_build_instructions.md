# iOS App Build Guide

Complete guide to build and run the **MusicAssistantClient** iOS app from source.

---

## Environment Verified

| Tool | Required | Tested Version |
|------|----------|----------------|
| macOS | 12+ | macOS 26 (Sequoia) |
| Xcode | 15+ | 26.2 (Build 17C52) |
| Xcode Command Line Tools | required | included with Xcode 26.2 |
| JDK | **17 or 21 LTS only** | Temurin 21.0.10 |
| Swift | 5.9+ | 6.2.3 (included with Xcode) |

> **Critical:** JDK 25 is **not** supported by Gradle 8.13 + Kotlin 2.3.0. Use JDK 21 LTS.
> Install Temurin 21: https://adoptium.net/temurin/releases/?version=21

---

## Prerequisites

### 1. Install JDK 21

```bash
# Verify installed JDKs
/usr/libexec/java_home -V

# Set JAVA_HOME to JDK 21 for the build session
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

If JDK 21 is not installed, download from https://adoptium.net/temurin/releases/?version=21

### 2. Install Xcode

Install Xcode 15 or later from the Mac App Store. Accept the license:

```bash
sudo xcodebuild -license accept
```

### 3. Make Gradle wrapper executable (one-time)

```bash
chmod +x ./gradlew
```

---

## Project Configuration

### 4. Set signing team and bundle ID

Edit `iosApp/Configuration/Config.xcconfig`:

```
TEAM_ID=YOUR_APPLE_TEAM_ID
BUNDLE_ID=io.music_assistant.client.MusicAssistantClient
APP_NAME=MusicAssistantClient
IPHONEOS_DEPLOYMENT_TARGET = 15.0
```

Replace `YOUR_APPLE_TEAM_ID` with your Apple Developer Team ID (10-character alphanumeric string found at developer.apple.com/account).

To build for simulator **without a developer account**, you can leave `TEAM_ID` empty and pass signing override flags on the command line (see step 6 below).

### 5. WebRTC framework (one-time download)

The app uses `webrtc-kmp` which requires the `WebRTC.xcframework` binary to be present at `iosApp/Frameworks/WebRTC.xcframework`.

The framework is already included in the repository at that path. If it's missing, download it:

```bash
# Download WebRTC.xcframework (M125 build, ~41 MB)
curl -L "https://github.com/webrtc-sdk/Specs/releases/download/125.6422.02/WebRTC.xcframework.zip" \
  -o /tmp/WebRTC.xcframework.zip

# Extract into iosApp/Frameworks/
mkdir -p iosApp/Frameworks
cd iosApp/Frameworks && unzip /tmp/WebRTC.xcframework.zip
```

Expected result: `iosApp/Frameworks/WebRTC.xcframework/` containing slices for:
- `ios-arm64` (physical device)
- `ios-arm64_x86_64-simulator` (simulator)

The Xcode build phase script (`Compile Kotlin Framework`) automatically:
1. Copies the correct slice into the KMP output directory (so the linker can find it)
2. Embeds `WebRTC.framework` into the `.app` bundle's `Frameworks/` folder (so dyld can find it at runtime)
3. Signs `WebRTC.framework` using `$EXPANDED_CODE_SIGN_IDENTITY` — the developer cert for device builds, ad-hoc (`-`) for simulator builds

---

## Build Commands

### 6. Build for iOS Simulator (command line)

```bash
# From the mobile-app/ directory:
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGN_IDENTITY=- \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build
```

The Xcode build phase will automatically run:
```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```
to compile the KMP framework before Swift compilation.

**First build time:** 10–30 minutes (Kotlin/Native compilation with `kotlin.native.cacheKind=none`).
**Subsequent builds:** 1–3 minutes (incremental, Gradle up-to-date checks).

### 7. Build and run in Xcode (recommended)

```bash
open iosApp/iosApp.xcodeproj
```

Then in Xcode:
1. Select a simulator or physical device from the toolbar
2. Click the **Run** button (⌘R)

Xcode will invoke the Gradle build phase automatically.

### 8. Build for physical device

Physical device builds require valid provisioning:
- Set `TEAM_ID` in `Config.xcconfig`
- Ensure your Apple Developer account can sign for `BUNDLE_ID`
- Remove `CODE_SIGNING_ALLOWED=NO` from the xcodebuild command
- The device must be registered in your Apple Developer portal

---

## Install and Run on Simulator

```bash
# List available simulators
xcrun simctl list devices available

# Boot a simulator (use UUID from list above)
xcrun simctl boot <SIMULATOR_UUID>

# Install the app
xcrun simctl install <SIMULATOR_UUID> \
  ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator/MusicAssistantClient.app

# Launch the app
xcrun simctl launch <SIMULATOR_UUID> io.music_assistant.client.MusicAssistantClient
```

---

## Project Structure

```
mobile-app/
├── iosApp/
│   ├── iosApp.xcodeproj/          # Xcode project
│   ├── iosApp/                    # Swift source files
│   │   ├── iOSApp.swift           # SwiftUI @main entry point
│   │   └── ContentView.swift      # Hosts KMP ComposeUIViewController
│   ├── AudioDecoders.swift        # PCM/Opus/FLAC decoders (AudioQueue)
│   ├── NativeAudioController.swift # AudioQueue-based PCM player
│   ├── NowPlayingManager.swift    # Lock screen / Control Center
│   ├── Configuration/
│   │   └── Config.xcconfig        # TEAM_ID, BUNDLE_ID, APP_NAME
│   └── Frameworks/
│       └── WebRTC.xcframework     # WebRTC M125 binary (125.6422.02)
├── composeApp/
│   ├── build.gradle.kts           # KMP module build config
│   └── src/
│       ├── commonMain/            # Shared Kotlin/Compose UI + logic
│       ├── androidMain/           # Android-specific implementations
│       └── iosMain/               # iOS-specific Kotlin glue
│           ├── MainViewController.kt
│           ├── di/IosModule.kt
│           └── player/MediaPlayerController.ios.kt
├── gradle.properties              # kotlin.native.cacheKind=none
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Key Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Kotlin Multiplatform | 2.3.0 | Cross-platform language + toolchain |
| Compose Multiplatform | 1.9.3 | Shared UI (Compose for iOS/Android) |
| Ktor | 3.3.3 | HTTP + WebSocket client (Darwin engine on iOS) |
| Koin | 4.1.1 | Dependency injection |
| kotlinx.coroutines | 1.10.2 | Async/concurrency |
| Coil3 | 3.3.0 | Image loading |
| Kermit | 2.0.8 | Multiplatform logging |
| webrtc-kmp | 0.125.11 | WebRTC remote access (iOS: stubs only) |
| swift-opus | 0.0.2 | Opus audio decoding (SPM) |
| flac-binary-xcframework | 0.2.0 | FLAC decoding (SPM) |
| ogg-binary-xcframework | 0.1.3 | Ogg container (SPM) |
| **WebRTC.xcframework** | **125.6422.02** | **Required binary — see step 5** |

SPM packages are resolved automatically by Xcode at first build.

---

## Gradle Build Tasks

```bash
# Build the KMP iOS framework manually (normally done by Xcode build phase)
# Must set these env vars when running outside Xcode:
JAVA_HOME=.../temurin-21.jdk/Contents/Home \
CONFIGURATION=Debug \
SDK_NAME=iphonesimulator \
ARCHS=arm64 \
EXPANDED_CODE_SIGN_IDENTITY=- \
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode

# Run KMP unit tests (Android + common)
./gradlew :composeApp:testDebugUnitTest

# Lint
./gradlew lintDebug
```

---

## Fixes Applied to Source Code

The following issues were found and fixed to enable iOS/Kotlin-Native compilation:

### 1. `System.currentTimeMillis()` in commonMain

**File:** `composeApp/src/commonMain/kotlin/io/music_assistant/client/api/ServiceClient.kt`
**Problem:** `System.currentTimeMillis()` is JVM-only; Kotlin/Native has no `System` class.
**Fix:** Replaced with `currentTimeMillis()` from the existing `expect/actual` in `utils/PlatformTime.kt`.

### 2. `synchronized()` in commonMain

**File:** `composeApp/src/commonMain/kotlin/io/music_assistant/client/player/sendspin/audio/AudioStreamManager.kt`
**Problem:** `synchronized(lock) {}` is JVM-only; not available in Kotlin/Native.
**Fix:** Replaced `private val decoderLock = Any()` with `private val decoderLock = Mutex()` and all `synchronized(decoderLock) { }` blocks with `decoderLock.withLock { }` (suspend functions) or `runBlocking { decoderLock.withLock { } }` (`close()` non-suspend). Non-local `return` inside the old `synchronized` lambdas was replaced with `return@withLock null` + `?: return`.

### 3. `PRODUCT_BUNDLE_IDENTIFIER` placeholder

**File:** `iosApp/iosApp.xcodeproj/project.pbxproj`
**Problem:** Bundle ID was hardcoded to `com.yourname.musicassistant` (placeholder).
**Fix:** Changed to `"$(BUNDLE_ID)"` to use the value from `Config.xcconfig`.

### 4. `TEAM_ID` empty in Config.xcconfig

**File:** `iosApp/Configuration/Config.xcconfig`
**Fix:** Populated with the team ID from the existing `DEVELOPMENT_TEAM` in the pbxproj.

### 5. `WebRTC.framework` not embedded in app bundle (dyld crash on launch)

**File:** `iosApp/iosApp.xcodeproj/project.pbxproj` — `Compile Kotlin Framework` shell script
**Problem:** The build phase script copied `WebRTC.framework` to the linker search path (so the build succeeded), but never embedded it into the `.app` bundle. At runtime, dyld looked for it at `@executable_path/Frameworks/WebRTC.framework`, found nothing, and aborted with:
```
#3  dyld4::halt()
#4  dyld4::prepare()
#5  start()
```
**Fix:** Added to the end of the shell script:
```sh
BUNDLE_FRAMEWORKS="$TARGET_BUILD_DIR/$CONTENTS_FOLDER_PATH/Frameworks"
mkdir -p "$BUNDLE_FRAMEWORKS"
cp -Rf "$OUTPUT_DIR/WebRTC.framework" "$BUNDLE_FRAMEWORKS/"
```

### 6. `WebRTC.framework` signature invalid on physical device

**File:** `iosApp/iosApp.xcodeproj/project.pbxproj` — `Compile Kotlin Framework` shell script
**Problem:** After fix #5, a simulator build worked but deploying to a physical device failed with error `0xe8008014 (The executable contains an invalid signature)` because the framework was initially unsigned (error `0xe800801c`) and then signed with a hardcoded ad-hoc identity (`-`) which physical devices reject.
**Fix:** Sign the embedded framework using `$EXPANDED_CODE_SIGN_IDENTITY` — the Xcode build environment variable that holds the actual developer certificate for device builds and `-` (ad-hoc) for simulator builds:
```sh
codesign --force --sign "${EXPANDED_CODE_SIGN_IDENTITY:--}" "$BUNDLE_FRAMEWORKS/WebRTC.framework"
```
The `:--` fallback ensures ad-hoc signing if the variable is empty (e.g., CLI simulator builds with `CODE_SIGNING_ALLOWED=NO`).

---

## Troubleshooting

### `BUILD FAILED: 25.0.2` (Gradle)

JDK 25 is not supported. Explicitly set `JAVA_HOME` to JDK 21:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

### `Could not infer iOS target platform` (Gradle)

This happens when running `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` directly without Xcode env vars. Either run the build through Xcode, or set all required env vars:
```bash
CONFIGURATION=Debug SDK_NAME=iphonesimulator ARCHS=arm64 EXPANDED_CODE_SIGN_IDENTITY=- ./gradlew ...
```

### `Undefined symbols: _OBJC_CLASS_$_RTCAudioTrack` (Linker)

WebRTC.xcframework is missing from `iosApp/Frameworks/`. Follow step 5 above to download and extract it.

### `The project is damaged and cannot be opened due to a parse error`

Variable references in `project.pbxproj` must be quoted. e.g., use `"$(BUNDLE_ID)"` not `$(BUNDLE_ID)`.

### App crashes immediately on launch — `dyld4::halt()` / `dyld4::prepare()` in backtrace

`WebRTC.framework` is not embedded in the app bundle. Verify the `Compile Kotlin Framework` build phase script contains the embed + sign block at the bottom (fixes #5 and #6 above). A clean build (`Product > Clean Build Folder`) then rebuild should resolve it.

### `Failed to verify code signature … WebRTC.framework : 0xe800801c (No code signature found.)`

The framework was embedded but not signed. The `codesign` line is missing from the build phase script — see fix #6 above.

### `Failed to verify code signature … WebRTC.framework : 0xe8008014 (The executable contains an invalid signature.)`

The framework was signed with an ad-hoc identity (`-`) but is being installed on a physical device which requires a developer certificate. The `codesign` line must use `"${EXPANDED_CODE_SIGN_IDENTITY:--}"` (not a hardcoded `-`) — see fix #6 above.

### `Unresolved reference 'synchronized'` or `'System'` (Kotlin/Native)

JVM-only APIs in `commonMain`. Use KMP-compatible alternatives (`Mutex.withLock { }`, `currentTimeMillis()`).

### Long first build (10–30 min)

`kotlin.native.cacheKind=none` in `gradle.properties` disables all Kotlin/Native caching. This is intentional. Subsequent builds are faster (1–3 min) due to incremental compilation.

---

## Known Limitations (iOS)

- **Local playback** (`MediaPlayerController.ios.kt`): stub only — AVFoundation/AVPlayer not yet implemented
- **WebRTC** (`DataChannelWrapper.ios.kt`, `PeerConnectionWrapper.ios.kt`): throws `NotImplementedError` — iOS WebRTC not yet implemented
- **OAuth** (`OAuthHandler.ios.kt`): throws `UnsupportedOperationException`
- **Background audio** / lock screen controls: infrastructure is present (`NowPlayingManager.swift`, `NativeAudioController.swift`) but requires AVFoundation player implementation to activate
