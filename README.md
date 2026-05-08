# Music Assistant Mobile app

The (official) Music Assistant Mobile app is a cross-platform client application designed for Android and iOS. Developed using Kotlin Multiplatform (KMP) and Compose Multiplatform frameworks, this project aims to provide a unified codebase for seamless music management across mobile platforms.

Please note that this project is still under (heavy) development and not yet in a production state or published to any of the app stores. Development work is in progress to allow this project to become the official mobile app for the Music Assistant project.

This client interfaces with the [Music Assistant Server](https://github.com/music-assistant/server), an open-source media library manager that integrates with various streaming services and connected speakers. The server acts as the core component, running on devices like Raspberry Pi, NAS, or Intel NUC, and facilitates centralized music management.

By leveraging the capabilities of KMP and Compose Multiplatform, Music Assistant Kmp Client offers a consistent and efficient user experience across different platforms, simplifying the development process and ensuring feature parity.

## Design goals

The goal of this app is to provide an iOS and Android native feeling experience for playing and controlling audio via Music Assistant on both Android/iOS phones and tablets. This means that as well as making it easy to navigate the MA library and providers, this shouldn't come at the expense of being able to quickly control players. Specifically, these actions should be available as quickly as possible when opening the app in pretty much any state:

- See what's playing on the app's current player
- Change the current player
- Play/pause current player
- Transfer playback to another player
- Group current player with another player
- Navigate to "global" (across all providers) search
- Return to home screen
- Skip to next track
- Start browsing library
- Open settings

Compatibility with Apple CarPlay and Android Auto is a core focus, ensuring you can enjoy your music easily and safely through your car’s infotainment system. These in-car experiences are dedicated exclusively to the local player; managing external players is out of scope.

## Current set of features:

### Disclaimer: This app is not intended to provide offline playback

- All platforms:
  - managing MA players queues and playback;
  - managing dynamic and static groups (no static group creation);
  - local playback on device from MA library (Sendspin streaming protocol over WebRTC or WebSocket);
  - comprehensive Settings screen with section-based UI for server connection, authentication (builtin/OAuth), and local player configuration.
- Android-specific:
  - requires Android 8.0 or later; 
  - media service (background playback) and media notification in system area for quick access to players controls;
  - Android Auto support for built-in player.
- iOS-specific:
  - requires iOS 18.5 or later; 
  - native audio playback via AudioQueue (CoreAudio) with support for FLAC, Opus, and PCM;
  - Lock Screen and Control Center integration (Now Playing info, play/pause/next/prev remote commands);
  - background audio playback with automatic resume after phone call or Siri interruptions;
  - WebRTC data channel transport for low-latency Sendspin streaming.

## Contributing

The project is in an early stage of development. Any help (especially from designers and iOS developers) is appreciated. To contribute:

1. [Find an issue](https://github.com/music-assistant/mobile-app/issues) to work on - if you've noticed something wrong or missing, please file an issue about it
2. Ask in the issue if you can work on it - this prevents multiple people from working on an issue at the same time
3. Submit a PR with "Closes #<issue number>" at the top of the description

### Structure

The project currently supports the iOS and Android targets. Common code is held within a KMP library module (`composeApp`) which the two platform specific app modules then depend on (`androidApp` and `iosApp`).

### Building from source

#### iOS

See [ios_build_instructions.md](ios_build_instructions.md) for a full step-by-step guide covering:

- Required tools and JDK version (JDK 21 LTS required — JDK 25 is not supported)
- WebRTC framework setup
- Signing and provisioning configuration
- Build commands for simulator and physical device
- Known limitations and troubleshooting

#### Android

To build the app:

```bash
./gradlew :androidApp:assembleDebug
```

To build a non-debuggable "release mode" APK for testing performance using your local debug keystore:

```bash
./gradlew :androidApp:assembleSelfSignedRelease
```

### Writing/running tests

Tests for shared multiplatform code live in the `composeApp` module's `commonTest` source set. These can be run locally in the JVM for the Android target using `./gradlew :composeApp:testAndroidHostTest`.

Tests for Compose UI code are in the `androidApp` module. This is because [multiplatform Compose testing is currently still experimental](https://kotlinlang.org/docs/multiplatform/compose-test.html) and tests written using the multiplatform approach cannot be easily run in a local JVM yet without the desktop target (which this project doesn't use). The Compose tests can be run with `./gradlew :androidApp:testDebug`.
## Want to try it?

Download and install debug APK from latest release on [releases page](https://github.com/music-assistant/kmp-client-app/releases).

*Disclamer: this is debug version of application, and isn't recommended for usage beyond testing purposes!*

### To use the app with Android Auto you will need additional steps
   - in Android Auto menu on your phone, click repeatedly on `Version and permission info` text, until dialog appears, that will allow you turning dev mode on;
   - after turning it on, in overflow menu (three dots on top) choose `Developer settings`;
   - in dev settings, find and enable `Unknown sources`;
   - after this, customize your launcher to show Music Assistant.
