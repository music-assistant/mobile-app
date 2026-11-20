# Music Assistant KMP Client

Cross-platform music player client for [Music Assistant Server](https://github.com/music-assistant/server).
Built with Kotlin Multiplatform + Compose Multiplatform for Android and iOS.

## Quick Commands

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug

# iOS - open in Xcode
open iosApp/iosApp.xcodeproj

# Tests
./gradlew :composeApp:testAndroidHostTest
./gradlew :androidApp:testDebugUnitTest      # Robolectric UI suite
./gradlew :sendspin-kmp:testAndroidHostTest
CI=true ./gradlew detektAll
```

## Features

- Queue management and playback control for Music Assistant players
- Library browsing (Artists, Albums, Tracks, Playlists, Audiobooks, Podcasts, Radio, Genres, Browse)
- Authentication: login/pass, OAuth, long-lived access token
- Remote access over WebRTC (see `.claude/webrtc-completion-summary.md`)
- Built-in player with Sendspin protocol support (`:sendspin-kmp`)
- Android Auto / CarPlay support
- Deep links, App Links and Universal Links (see `.claude/deep-links.md`)

## Architecture

@architecture.md
@project-structure.md
@dependencies.md
@guidelines.md

## UI Documentation

- **Settings Screen**: See `.claude/settings-screen.md` for complete documentation on server connection, authentication flows, and local player configuration
- **CarPlay & Siri**: See `.claude/carplay.md` for CarPlay architecture, template constraints, and the Siri media-domain integration (`INPlayMediaIntent` donation, `INUpdateMediaAffinityIntent`, `INSearchForMediaIntent`)
- **Deep links**: See `.claude/deep-links.md` for the `musicassistant://app` routes, the App Links and Universal Links, and the OAuth callback. The user reference is `docs/app-documentation/deep-links.md`
- **Feature parity**: See `.claude/feature-parity-report.md` for the listener-feature comparison with the web frontend on server 2.10 and the 2.11 beta, with the adjustments and the missing features
- **WebRTC Remote Access**: See `.claude/webrtc-completion-summary.md` for the shipped implementation, which includes Sendspin over WebRTC
- **Local player (Sendspin)**: See the Sendspin sections of `architecture.md` and `sendspin-kmp/README.md`. `.claude/ios_audio_pipeline.md` covers the iOS audio output