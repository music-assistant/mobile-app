# Dependencies

## UI
- **Compose Multiplatform** - Cross-platform UI
- **Material3** - Design system
- **MaterialKolor** (5.0.1) - Makes a Material 3 color scheme from one seed color. The accent color setting takes only the primary group from it.
- **Material Icons Extended** - Icon set
- **FontAwesome / Tabler Icons** - Additional icons

## Networking
- **Ktor** (3.5.2) - HTTP client + WebSocket. Engines: OkHttp on Android (`AndroidHttpClientFactory`), Darwin on iOS.
- **WebSocket** - Real-time server connection
- **Ktor WebRTC** (`io.ktor:ktor-client-webrtc`, the Ktor version) - Remote access tunneling on Android and iOS. It replaced `com.shepeliev:webrtc-kmp`.

## Data
- **kotlinx.serialization** - JSON serialization
- **multiplatform-settings-no-arg** - Cross-platform preferences

## DI & Architecture
- **Koin** - Dependency injection (core + compose + viewmodel)
- **Navigation3** - Type-safe navigation + Material3 adaptive

## Media
- **Coil 3.5.0** - Async image decoding and Compose integration; a pre-memory interceptor resolves HTTP and `mawebrtc` artwork through the app-owned `ArtworkRepository`, which supplies encoded bytes and content-based identities (no Coil network artifact). Coil 3.6.2 was reverted because it requires Compose 1.12 alignment.
- **androidx.media** (1.8.0) - `MediaSessionCompat` and `MediaBrowserServiceCompat` for the media services and Android Auto
- **Concentus** (1.0.2, `sendspin-kmp`) - Opus decoder on Android
- **KMPalette** (3.1.0) - Dominant color of the artwork
- Local playback has no player library. The Sendspin player writes to `AudioTrack` on Android and to `AudioQueue` (`NativeAudioController.swift`) on iOS.

## Security
- **cryptography-kotlin** (0.6.0) - Crypto primitives (JDK provider on Android, CryptoKit on iOS)
- **BouncyCastle** (`bcprov-jdk18on` 1.85.2, `sendspin-kmp`) - X25519 and ChaCha20-Poly1305 on Android

## Wear OS (`wearApp` only)
- **androidx.wear-ongoing** (1.0.0) - `OngoingActivity`, the playing chip. Without one, Wear evicts an ambient app after 60 s.
- **androidx.wear** (1.3.0) - `androidx.wear.ambient.AmbientLifecycleObserver`, which opts the watch activity into the always-on screen and reports its low-bit requirement. Its own release line, not the Wear Compose one; nothing else in the artifact is used. Wear Compose's own `rememberAmbientModeManager` would replace it, but that goes through the Wear SDK unguarded and so needs a newer watch than this module's `minSdk` of 33.
- **Wear Compose** (1.7.1) - `compose-material3` (Material 3 Expressive for watches), `compose-foundation` (`TransformingLazyColumn`, rotary), `compose-navigation` (`SwipeDismissableNavHost`). Pinned against the androidx Compose artifacts (1.12.1), not Compose Multiplatform: the watch module shares no UI with the phone app.
- **play-services-wearable** (19.0.0) - The Wearable Data Layer. The phone app (`composeApp` androidMain, `WearDataLayerLink`) puts the server and the token in a data item, and the watch (`PhoneLinkStore`) reads it. `kotlinx-coroutines-play-services` gives the watch `Task.await()`.
- **material-color-utilities** (5.0.1) - The HCT/quantizer half of MaterialKolor, without its Compose Material3 surface. Seeds the watch's `androidx.wear.compose.material3.ColorScheme` from the cover art.

## watchOS (`watchShared` + `watchApp` only)
- **WatchConnectivity** (system framework) - The iPhone app (`composeApp` iosMain, `WatchConnectivityLink`) sends the server and the token as the application context, and `PhoneConfigReceiver.swift` reads it. The static `ComposeApp` framework autolinks it.
- **Ktor Darwin** - `NSURLSession` behind the Ktor client, including its WebSocket. The one engine that builds for the watchOS targets; the Wear app uses OkHttp for the same job.
- No UI library. The watch app is SwiftUI against the `MaWatchKit` framework, and nothing else. There is no equivalent of the Wear app's **material-color-utilities**: the album reaches the screen as the blurred cover behind it rather than as a generated colour scheme, so nothing is quantized or scored.

## Utilities
- **Kermit** - Multiplatform logging
- **QRose encoder** (`qrose-encoder-matrix` 1.3.1) - Makes the QR bit matrix of the party invite. It has no Compose dependency. `QrCode` draws the matrix.
- **Reorderable** (3.1.0) - Drag-drop list reordering
- **EasyQRScan** (0.7.1) - QR code scanner
- **mDNS** - No library. Server discovery uses the platform API: `NsdManager` on Android, `NWBrowser` (`MassServiceBrowser.swift`) on iOS.

## Build
- **Gradle** - Build system
- **Kotlin Multiplatform** - Cross-platform compilation
- **JVM 17** - Target for Android

---

> **Maintenance**: Update this file when adding/removing libraries.