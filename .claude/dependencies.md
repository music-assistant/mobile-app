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