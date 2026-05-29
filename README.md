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

*Disclaimer: This app is not intended to provide offline playback*

- All platforms:
  - managing MA players queues and playback;
  - managing dynamic and static groups (no static group creation);
  - local playback on device from MA library (Sendspin streaming protocol over WebRTC or WebSocket);
  - comprehensive Settings screen with section-based UI for server connection, authentication (builtin/OAuth), and local player configuration.
- Android-specific:
  - requires Android 8.0 or later; 
  - media service (background playback) and media notification in system area for quick access to players controls;
  - Android Auto support for the built-in player, plus voice playback ("Hey Google, play *X* on Music Assistant") via Google Assistant and Gemini App Actions — works in the car *and* on the phone, single symmetric code path. Full phrase reference, architecture notes and testing in [ANDROID-AUTO.md](docs/ANDROID-AUTO.md).
- iOS-specific:
  - requires iOS 18.5 or later; 
  - native audio playback via AudioQueue (CoreAudio) with support for FLAC, Opus, and PCM;
  - Lock Screen and Control Center integration (Now Playing info, play/pause/next/prev remote commands);
  - background audio playback with automatic resume after phone call or Siri interruptions;
  - WebRTC data channel transport for low-latency Sendspin streaming;
  - Apple CarPlay support for built-in player (very early state, a lot of bugs are expected).

## Want to try it?

Download and install debug APK from latest release on [releases page](https://github.com/music-assistant/kmp-client-app/releases).

*Disclaimer: this is debug version of application, and isn't recommended for usage beyond testing purposes!*

iOS users can join [TestFlight](https://testflight.apple.com/join/4byCu2Nk).

## Android Auto & Google Assistant

See [ANDROID-AUTO](docs/ANDROID-AUTO.md) for:

- enabling Android Auto with sideloaded debug/self-signed builds (Unknown sources flow);
- the library browsing layout on the head unit;
- the full list of supported Google Assistant voice phrases (works in the car *and* on the phone);
- shell-based testing of `MEDIA_PLAY_FROM_SEARCH` intents;
- known limitations.

## Configuring and using the app

For information on configuring and using the Music Assistant Mobile app, see the [App Documentation](docs/app-documentation/index.md).