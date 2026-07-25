# Dependencies

## UI
- **Compose Multiplatform** - Cross-platform UI
- **Material3** - Design system
- **Material Icons Extended** - Icon set
- **FontAwesome / Tabler Icons** - Additional icons

## Networking
- **Ktor** - HTTP client + WebSocket (CIO/Darwin/Java engines)
- **WebSocket** - Real-time server connection
- **WebRTC KMP** (v0.125.11) - Remote access tunneling (✅ production ready on Android)
  - **Note**: Uses native `org.webrtc.DataChannel` API for TEXT message transmission (webrtc-kmp limitation workaround)

## Data
- **kotlinx.serialization** - JSON serialization
- **multiplatform-settings-no-arg** - Cross-platform preferences

## DI & Architecture
- **Koin** - Dependency injection (core + compose + viewmodel)
- **Navigation3** - Type-safe navigation + Material3 adaptive

## Media
- **Coil 3** - Async image loading (with Ktor network)
- **ExoPlayer (Media3)** - Android local playback
- **AVPlayer** - iOS local playback

## Utilities
- **Kermit** - Multiplatform logging
- **Reorderable** - Drag-drop list reordering
- **mDNS** - Sendspin service discovery/advertising

## Build
- **Gradle** - Build system
- **Kotlin Multiplatform** - Cross-platform compilation
- **JVM 17** - Target for Android

---

> **Maintenance**: Update this file when adding/removing libraries.