# iOS App Build Guide

This guide tells you how to build and run the iOS app from source.
Run all commands from the repository root.

---

## Requirements

| Tool | Requirement | Reason |
|------|-------------|--------|
| Mac | Apple silicon | The Kotlin build has the `iosArm64` and `iosSimulatorArm64` targets only. There is no Intel simulator slice. |
| Xcode | A version with the iOS 27.1 SDK or later | The Swift code uses `UIHingeInteraction` and other iOS 27.1 APIs. |
| iOS | 18.5 or later on the device or simulator | The `iosApp` target sets `IPHONEOS_DEPLOYMENT_TARGET` to 18.5. |
| JDK | 21 | The CI workflows use Temurin 21. The Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) runs on this JDK. |

The versions of Kotlin, Compose Multiplatform, Ktor, Koin and the other libraries are in `gradle/libs.versions.toml`.

---

## Prerequisites

### 1. Install Xcode

Install Xcode from the Mac App Store or from developer.apple.com. Accept the license:

```bash
sudo xcodebuild -license accept
```

### 2. Select the Xcode developer directory

Check the active developer directory:

```bash
xcode-select -p
```

If the result is `/Library/Developer/CommandLineTools`, the Kotlin/Native tasks cannot find the iOS SDK.
Select Xcode:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Instead of the global change, you can set `DEVELOPER_DIR` for one command:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew iosSimulatorArm64Test
```

### 3. Install JDK 21

List the installed JDKs:

```bash
/usr/libexec/java_home -V
```

If JDK 21 is not in the list, get Temurin 21 from https://adoptium.net/temurin/releases/?version=21.
Then set `JAVA_HOME` for the shell:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### 4. Give JAVA_HOME to Xcode

The Xcode build phase `Compile Kotlin Framework` runs Gradle with the JDK in `$JAVA_HOME`.
Xcode gets `JAVA_HOME` from the environment that starts it.
If you start Xcode from the Dock, Xcode does not read your shell configuration.
Use one of these methods:

- Start Xcode from a shell that has `JAVA_HOME`, for example `open iosApp/iosApp.xcodeproj`.
- Set the value for all apps that launchd starts: `launchctl setenv JAVA_HOME "$(/usr/libexec/java_home -v 21)"`.
- Add a line `JAVA_HOME = <path to the JDK 21 home>` to `iosApp/Configuration/Local.xcconfig`. Xcode gives build settings to the build phase scripts as environment variables.

### 5. Download the WebRTC framework

The app links `WebRTC.xcframework` from `iosApp/Frameworks/`. Git ignores this directory, so you must download the framework one time.
The build uses a patched M125 build from `teancom/webrtc`. It has the `DcSctpTransport` receive buffer fix from `webrtc-sdk/webrtc#234`.

```bash
curl -L --fail \
  "https://github.com/teancom/webrtc/releases/download/m125-cow-bloat-fix.1/WebRTC.xcframework.m125-cow-bloat-fix.1.zip" \
  -o /tmp/WebRTC.xcframework.zip
mkdir -p iosApp/Frameworks
unzip -q /tmp/WebRTC.xcframework.zip -d iosApp/Frameworks/
rm /tmp/WebRTC.xcframework.zip
```

Check that these slices exist:

- `iosApp/Frameworks/WebRTC.xcframework/ios-arm64` (device)
- `iosApp/Frameworks/WebRTC.xcframework/ios-arm64_x86_64-simulator` (simulator)

If you change the WebRTC release, change it in all of these files:

- `.github/workflows/check-pr-ios.yml`
- `.github/workflows/ios-release.yml`
- `iosApp/ci_scripts/ci_post_clone.sh`
- this guide

---

## How Xcode builds the Kotlin code

The `iosApp` target has a Run Script build phase, `Compile Kotlin Framework`. It runs before the Swift compile. The script does these steps:

1. It calculates a hash of the Kotlin sources, the Compose resources and the Gradle build files. If the hash did not change since the last successful run, it skips Gradle.
2. Otherwise, it runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. This task builds the static `ComposeApp.framework` into `composeApp/build/xcode-frameworks/<configuration>/<sdk>/`.
3. It copies the applicable `WebRTC.framework` slice into the same directory, so the linker finds it.
4. It copies `WebRTC.framework` into the `Frameworks` directory of the app bundle, so dyld finds it at runtime.
5. It signs the embedded `WebRTC.framework` with `$EXPANDED_CODE_SIGN_IDENTITY`. If the value is empty, it uses the ad-hoc identity (`-`).
6. It copies the Compose Multiplatform resources into `compose-resources` in the app bundle.

The script stops at the first failed command (`set -e`). So a Gradle error fails the Xcode build.

If `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` is `YES`, the script stops before step 1 and does nothing.

---

## Build and run

### Xcode

```bash
open iosApp/iosApp.xcodeproj
```

1. Select the `iosApp` scheme.
2. Select a simulator or a device.
3. Click **Run** (⌘R).

The first build compiles all Kotlin/Native code, so it takes much longer than the subsequent builds.

### Command line, simulator

This command builds the app for the simulator without signing. The CI builds the app in the same way.

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build
```

To install and start the app on a simulator:

```bash
# List the available simulators
xcrun simctl list devices available

# Start a simulator
xcrun simctl boot <SIMULATOR_UUID>

# Install the app
xcrun simctl install <SIMULATOR_UUID> \
  ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator/MusicAssistantClient.app

# Start the app (default bundle identifier)
xcrun simctl launch <SIMULATOR_UUID> io.music-assistant.mobile-client
```

If you set `MA_BUNDLE_ID`, use that value in the `launch` command.

---

## Signing

`iosApp/Configuration/Config.xcconfig` holds the signing values. The defaults build the official app with the team and the profile of the Music Assistant project.
To build with your own Apple developer account, set these values:

| Value | Meaning | Default |
|-------|---------|---------|
| `MA_TEAM_ID` | Your Apple developer team identifier | The team of the official app |
| `MA_BUNDLE_ID` | The bundle identifier, for example `com.example.music-assistant` | `io.music-assistant.mobile-client` |
| `MA_CARPLAY` | `YES` signs with the CarPlay audio entitlement. `NO` signs without it. | `YES` |
| `MA_SIGNING_STYLE` | `Manual` or `Automatic`. Only the Release configuration uses it. | `Manual` |

Apple grants the CarPlay audio entitlement to each account on request. If your account does not have it, set `MA_CARPLAY = NO`.
The Debug configuration always uses automatic signing. The Release configuration with `Manual` needs the "Music Assistant App Store" profile, so set `MA_SIGNING_STYLE = Automatic` for your own release builds.

Put the values in `iosApp/Configuration/Local.xcconfig`. Git ignores this file, and `Config.xcconfig` includes it. For example:

```
MA_TEAM_ID = ABCDE12345
MA_BUNDLE_ID = com.example.music-assistant
MA_CARPLAY = NO
MA_SIGNING_STYLE = Automatic
```

You can also give the values to `xcodebuild` as environment variables. Xcode.app does not see shell variables, so use `Local.xcconfig` for Xcode.app.

For a device build, register the device in your Apple developer account. Your account must be able to sign for the bundle identifier.

### Xcode Cloud

`iosApp/ci_scripts/ci_post_clone.sh` runs after Xcode Cloud clones the repository. It does these steps:

- It downloads `WebRTC.xcframework`.
- It installs JDK 21, because Xcode Cloud has no JDK.
- It writes `Local.xcconfig` with `JAVA_HOME`, `MA_SIGNING_STYLE = Automatic` and the `MA_TEAM_ID`, `MA_BUNDLE_ID` and `MA_CARPLAY` workflow environment variables.
- It sets the build number to `CI_BUILD_NUMBER`, because TestFlight needs a new build number for each upload.

The official release builds on GitHub Actions (`.github/workflows/ios-release.yml`), not on Xcode Cloud.

---

## Project structure

```
.
├── iosApp/
│   ├── iosApp.xcodeproj/                # Xcode project
│   ├── iosApp/                          # App sources, Info.plist, entitlements
│   │   ├── iOSApp.swift                 # SwiftUI entry point
│   │   ├── ContentView.swift            # Hosts the Compose UI
│   │   └── CarPlay/                     # CarPlay scene and Siri intent handler
│   ├── AudioDecoders.swift              # PCM, Opus and FLAC decoders
│   ├── NativeAudioController.swift      # AudioQueue player
│   ├── NowPlayingCoordinator.swift      # Lock Screen and Control Center
│   ├── OAuthWebSession.swift            # OAuth sign-in session
│   ├── iosAppTests/                     # XCTest suite
│   ├── ci_scripts/ci_post_clone.sh      # Xcode Cloud hook
│   ├── Configuration/
│   │   ├── Config.xcconfig              # Deployment target, version, signing
│   │   └── Local.xcconfig               # Your overrides (git-ignored)
│   └── Frameworks/
│       └── WebRTC.xcframework           # Downloaded in step 5 (git-ignored)
├── composeApp/
│   └── src/
│       ├── commonMain/                  # Shared Kotlin and Compose code, including webrtc/
│       └── iosMain/                     # iOS Kotlin code
│           ├── MainViewController.kt
│           ├── di/                      # IosModule.kt, KmpHelper.kt (Swift bridge)
│           └── player/local/            # AudioQueueSink.kt (built-in player sink)
├── sendspin-kmp/                        # Sendspin player library
├── gradle/libs.versions.toml            # Library versions
└── scripts/test-ios.sh                  # Runs the XCTest suite
```

Xcode gets the Swift packages `swift-opus`, `flac-binary-xcframework` and `ogg-binary-xcframework` at the first build.
The project file and `Package.resolved` hold their versions.

---

## Tests

Run the Kotlin/Native tests on the simulator:

```bash
./gradlew iosSimulatorArm64Test
```

Run the XCTest suite of the Swift code. The script needs an Apple silicon Mac and an available iPhone simulator.

```bash
./scripts/test-ios.sh
```

Check the public API of `sendspin-kmp`. This check needs macOS, because the API dump comes from the iOS klibs.

```bash
./gradlew :sendspin-kmp:checkKotlinAbi
```

To compile the debug framework for the simulator without Xcode:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

---

## Troubleshooting

### Gradle fails in the Xcode build phase with a Java error

Xcode did not get JDK 21. Refer to step 4.

### The Kotlin/Native tasks cannot find the iOS SDK

The developer directory is the Command Line Tools. Refer to step 2.

### `Undefined symbols: _OBJC_CLASS_$_RTC...` (linker)

`WebRTC.xcframework` is not in `iosApp/Frameworks/`. Refer to step 5.

### The app stops at launch with `dyld4::halt()` in the backtrace

`WebRTC.framework` is not in the app bundle. Make sure that `iosApp/Frameworks/WebRTC.xcframework` exists. Then select **Product > Clean Build Folder** and build again.

### The device rejects the app with a code signature error

Make sure that `MA_TEAM_ID` is your team and that your account can sign for `MA_BUNDLE_ID`. If your account does not have the CarPlay entitlement, set `MA_CARPLAY = NO`.

### The Xcode build does not include a Kotlin change

The build phase skips Gradle if the hash of the sources did not change. The hash includes the `.kt` and `.kts` files in `composeApp/src`, the Compose resources and the root Gradle files. It does not include the other modules, for example `sendspin-kmp`. After a change in a different module, select **Product > Clean Build Folder** and build again.
