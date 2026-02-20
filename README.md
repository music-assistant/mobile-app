# Music Assistant Mobile app

The (official) Music Assistant Mobile app is a cross-platform client application designed for Android and iOS. Developed using Kotlin Multiplatform (KMP) and Compose Multiplatform frameworks, this project aims to provide a unified codebase for seamless music management across mobile platforms.

Please note that this project is still under (heavy) development and not yet in a production state or published to any of the app stores. Development work is in progress to allow this project to become the official mobile app for the Music Assistant project.

This client interfaces with the [Music Assistant Server](https://github.com/music-assistant/server), an open-source media library manager that integrates with various streaming services and connected speakers. The server acts as the core component, running on devices like Raspberry Pi, NAS, or Intel NUC, and facilitates centralized music management.

By leveraging the capabilities of KMP and Compose Multiplatform, Music Assistant Kmp Client offers a consistent and efficient user experience across different platforms, simplifying the development process and ensuring feature parity.

## Current set of features:

- All platforms:
  - managing MA players queues and playback;
  - local playback on device from MA library (Sendspin streaming protocol over WebRTC or WebSocket);
  - comprehensive Settings screen with section-based UI for server connection, authentication (builtin/OAuth), and local player configuration.
- Android-specific:
  - media service (background playback) and media notification in system area for quick access to players controls;
  - Android Auto support for built-in player.
- iOS-specific:
  - native audio playback via AudioQueue (CoreAudio) with support for FLAC, Opus, and PCM;
  - Lock Screen and Control Center integration (Now Playing info, play/pause/next/prev remote commands);
  - background audio playback with automatic resume after phone call or Siri interruptions;
  - WebRTC data channel transport for low-latency Sendspin streaming.

## Building from source

### iOS

See [ios_build_instructions.md](ios_build_instructions.md) for a full step-by-step guide covering:

- Required tools and JDK version (JDK 21 LTS required — JDK 25 is not supported)
- WebRTC framework setup
- Signing and provisioning configuration
- Build commands for simulator and physical device
- Known limitations and troubleshooting

### Android

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

## Contributing

The project is in an early stage of development. Any help (especially from designers and iOS developers) is appreciated. To contribute:

1. [Find an issue](https://github.com/music-assistant/mobile-app/issues) to work on - if you've noticed something wrong or missing, please file an issue about it
2. Ask in the issue if you can work on it - this prevents multiple people from working on an issue at the same time
3. Submit a PR with "Closes #<issue number>" at the top of the description

### Building from source

#### iOS

See [ios_build_instructions.md](ios_build_instructions.md) for a full step-by-step guide covering:

- Required tools and JDK version (JDK 21 LTS required — JDK 25 is not supported)
- WebRTC framework setup
- Signing and provisioning configuration
- Build commands for simulator and physical device
- Known limitations and troubleshooting

#### Android

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

## Want to try it?

Download and install debug APK from latest release on [releases page](https://github.com/music-assistant/kmp-client-app/releases).

*Disclamer: this is debug version of application, and isn't recommended for usage beyond testing purposes!*

### To use the app with Android Auto you will need additional steps
   - in Android Auto menu on your phone, click repeatedly on `Version and permission info` text, until dialog appears, that will allow you turning dev mode on;
   - after turning it on, in overflow menu (three dots on top) choose `Developer settings`;
   - in dev settings, find and enable `Unknown sources`;
   - after this, customize your launcher to show Music Assistant.
