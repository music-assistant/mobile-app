# Sendspin Integration - Current Status

**Last Updated:** 2026-04-28

## Implementation Status

### ✅ Core Features - WORKING

- **WebSocket Connection** - Connects to Music Assistant server Sendspin endpoint
- **Proxy Mode** - Uses main connection (port 8095) + path `/sendspin` by default with token authentication
- **Custom Connection Mode** - Optional separate host/port configuration for standalone servers
- **Auto-Reconnect** - WebSocketHandler automatically reconnects on network failures (exponential backoff, 10 attempts max)
- **Network Resilience** - Aggressive keepalive (5s ping, 5s TCP keepalive) for VPN-like stability
- **Token Authentication** - Authenticates with server token before protocol handshake (proxy mode only)
- **Protocol Handshake** - auth ↔ auth_ok → client/hello ↔ server/hello exchange
- **Clock Synchronization** - NTP-style sync with monotonic time base & Kalman filter
- **Adaptive Buffering** - Network-aware dynamic buffer sizing (WebRTC NetEQ-inspired)
- **Playback Control** - Play, pause, resume, seek, next/previous track
- **Progress Reporting** - Periodic state updates to server (every 2 seconds)
- **Metadata Display** - Title, artist, album from stream/metadata
- **Timestamp Synchronization** - Chunks play at correct time

### ✅ Platform Support

#### Android
- **Audio Output**: AudioTrack (low-latency raw PCM)
- **PCM Streaming**: ✅ Working
- **Opus Decoding**: ✅ Working (Concentus library)
- **FLAC Decoding**: ✅ Working (MediaCodec, API 26+)
- **Volume Control**: ✅ MediaSession integration with system volume
- **Background Playback**: ✅ MainMediaPlaybackService with notifications
- **Android Auto**: ✅ Supported via AndroidAutoPlaybackService

#### iOS
- **Audio Output**: AudioQueue (CoreAudio) — native, MPV removed
- **PCM Streaming**: ✅ Working
- **Opus Decoding**: ✅ Working (swift-opus / libopus) — stereo decoding corrected and re-enabled as default in #288 (2026-04-27)
- **FLAC Decoding**: ✅ Working (libFLAC)
- **Volume Control**: ✅ Working in production (TestFlight) — internal mechanism needs re-verification (a 2026-02-20 source review claimed `getCurrentSystemVolume()` was a stub returning hardcoded `100`, but field testing on 2026-04-28 confirms volume up/down behaves correctly). Likely the path doesn't go through that helper. Investigation queued.
- **Background Playback**: ✅ Shipped (`AVAudioSession` interruption + route-change handlers in `NativeAudioController.swift:40-95`; resumes after phone calls/Siri/headphone disconnect; in production via TestFlight since 2026-04-19)
- **WebRTC remote**: ✅ Shipped 2026-04-24 — see `webrtc-completion-summary.md`
- **Sendspin over WebRTC**: ✅ Shipped — see `sendspin-webrtc-status.md`
- **Compose Metal background pause**: ✅ Shipped 2026-04-24 (#265) — prevents GPU command-buffer rejection while audio keeps the run loop alive in `UIBackgroundModes: audio`
- **CarPlay**: ✅ Shipped — cold-launch black-screen fix in #277 (2026-04-26). See `carplay.md` for outstanding Siri donation work.
- **Implementation**: AudioQueue rewrite completed 2026-01-23; interruption handling 2026-02-20; iOS WebRTC + Sendspin sync + Metal pause 2026-04-24; full TestFlight distribution since 2026-04-19 (see `ios_audio_pipeline.md`)

### ⚠️ Partially Implemented

- **Error Recovery** - Basic handling implemented, edge cases need improvement
- **Stream Restoration** - Auto-reconnect works, but playback gap during reconnection is unavoidable

### ❌ Not Implemented

- **Artwork Display** - Protocol support exists, no implementation
- **Visualizer** - Not implemented
- **mDNS Discovery** - Using direct connection instead (intentional design choice)

---

## Recent Additions

### 2026-01-16: Platform Expansion & Auto-Reconnect

#### iOS Full Implementation ✅
- Complete MPV-based audio pipeline
- FLAC, Opus, and PCM codec support via libmpv/FFmpeg
- Custom stream protocol (`sendspin://stream`) with RingBuffer
- Demuxer configuration for each codec type
- Full rewrite documented in `ios_audio_pipeline.md`

#### Android FLAC Decoder ✅
- MediaCodec-based implementation (API 26+)
- Native hardware acceleration where available
- Supports 16/24/32-bit output with bit depth conversion
- Handles codec header (STREAMINFO block) from server

#### Auto-Reconnect ✅
- WebSocketHandler automatic reconnection on network failures
- Exponential backoff: 500ms, 1s, 2s, 5s, 10s (max 10 attempts)
- Aggressive keepalive settings for network transition resilience
- Graceful degradation: continues from buffer during brief disconnects

#### Network Resilience ✅
- 5-second WebSocket ping interval (down from 30s)
- 5-second TCP keepalive time
- Connection state monitoring: `WebSocketState.Reconnecting(attempt)`
- Explicit disconnect flag prevents unwanted reconnection attempts

### 2026-01-05: Android Opus & Adaptive Buffering

#### 1. Opus Decoder for Android ✅

**Implementation:**
- Uses Concentus library v1.0.2 (pure Java/Kotlin, no JNI)
- Supports Opus 48kHz stereo and mono
- Handles 16/24/32-bit output formats
- Validates Opus constraints (sample rates: 8k/12k/16k/24k/48k)
- Graceful error handling (returns silence on bad packets)

**Files:**
- `OpusDecoder.android.kt` - Full implementation
- `SendspinCapabilities.kt` - Advertises Opus support to server
- `gradle/libs.versions.toml` - Concentus dependency
- `build.gradle.kts` - Android dependency

**Status:** ✅ **Working** - Server sends Opus streams, client decodes successfully

**Bandwidth Savings:**
- PCM stereo 48kHz 16-bit: ~1.5 Mbps
- Opus stereo 48kHz: ~64-128 kbps (configurable)
- Savings: ~90-95% less bandwidth

### 2. Adaptive Buffering ✅

**Implementation:**
- Dynamic buffer sizing based on RTT, jitter, and sync quality
- EWMA smoothing for RTT measurements
- Welford's online algorithm for jitter estimation
- Fast increase on network degradation (2s cooldown)
- Conservative decrease on sustained good conditions (30s cooldown)
- Oscillation prevention with hysteresis

**Algorithm:**
```
targetBuffer = (smoothedRTT × 2 + jitter × 4) × qualityMultiplier + dropPenalty
Bounds: 200ms (min) to 2000ms (max)
Ideal: 300ms for good conditions
```

**Components:**
- `AdaptiveBufferManager.kt` - Core adaptive logic
- `CircularBuffer<T>` - RTT history tracking (60 samples)
- `JitterEstimator` - Running variance calculation
- `AudioStreamManager.kt` - Integration with playback
- `BufferState` - Extended with adaptive metrics

**Metrics Tracked:**
- Smoothed RTT (EWMA)
- Jitter (RTT standard deviation)
- Drop rate (last 100 chunks)
- Underrun timestamps (last 10 events)
- Target buffer duration
- Current prebuffer threshold

**Status:** ✅ **Working** - Adapts to network conditions in real-time

**Example Logs:**
```
AdaptiveBufferManager: Buffer increased: target=350ms, prebuffer=175ms (RTT=10.9ms, jitter=9ms, dropRate=0%)
AudioStreamManager: Playback: 3333 chunks, buffer=4890ms (target=400ms)
```

---

## Current Architecture

```
MainDataSource
      ↓
SendspinClientFactory
• Validates settings & auth
• Builds SendspinConfig (proxy/custom mode)
• Creates SendspinClient
• Returns Result<SendspinClient>
      ↓
SendspinClient (Orchestrator)
├── SendspinWsHandler
│   • WebSocket connection management
│   • Auto-reconnect with exponential backoff
│   • Aggressive keepalive (5s ping, 5s TCP)
│
├── MessageDispatcher
│   • Protocol state machine
│   • Auth flow (proxy mode): auth → auth_ok → hello
│   • Message routing (hello, time, stream/*, command)
│   • Clock sync coordination
│   • Config: MessageDispatcherConfig
│
├── ReconnectionCoordinator ⭐ NEW
│   • Monitors WebSocket state
│   • StreamRecoveryState machine (Idle → AwaitingReconnect → RecoveryInProgress → Success/Failed)
│   • Preserves buffer during brief disconnects
│   • 5-second recovery timeout
│
├── StateReporter ⭐ NEW
│   • Periodic state reporting (every 2s)
│   • Reports SYNCHRONIZED with volume/mute
│   • Independent CoroutineScope
│
├── AudioPipeline (interface) ⭐ NEW
│   └── AudioStreamManager (implementation)
│       • Multi-threaded architecture:
│         - Default dispatcher: Decoding (producer)
│         - audioDispatcher: Playback (high-priority consumer)
│         - Default dispatcher: Adaptation (every 5s)
│       • Binary parsing & timestamp conversion
│       • AudioDecoder (Opus/FLAC/PCM)
│         - Android: Decode to PCM
│         - iOS: Passthrough to native AudioQueue (decoded in Swift via libFLAC / swift-opus)
│       • TimestampOrderedBuffer
│       • AdaptiveBufferManager
│       • MediaPlayerController integration
│
└── ClockSynchronizer
    • NTP-style sync with monotonic time
    • Offset tracking & quality assessment
    • RTT validation & jitter measurement
          ↓
    MediaPlayerController
    • Android: AudioTrack (Raw PCM)
    • iOS: AudioQueue + Swift decoders (libFLAC, swift-opus, native PCM); MPV removed 2026-01-23
          ↓
    Audio Output
```

### Error Handling ⭐ NEW
- **SendspinError** sealed class for categorized errors:
  - `Transient(cause, willRetry)`: Auto-recoverable (network interruptions)
  - `Permanent(cause, userAction)`: Requires intervention (bad config, auth failure)
  - `Degraded(reason, impact)`: Limited functionality (high latency, packet drops)

---

## Connection Modes

### Proxy Mode (Default)
**How it works:**
- Uses main WebSocket connection settings (host, port, TLS)
- Connects to `/sendspin` path on main server
- Default port: 8095 (same as main API)
- Requires authentication before protocol handshake

**Authentication Flow:**
1. WebSocket connects to `ws(s)://host:8095/sendspin`
2. Client sends `auth` message: `{ "type": "auth", "token": "<user_token>", "client_id": "<sendspin_id>" }`
3. Server validates token and responds: `{ "type": "auth_ok" }`
4. Client proceeds with `client/hello` handshake
5. Server responds with `server/hello`

**Requirements:**
- User must be logged in (token available)
- Only starts after successful main connection authentication

**Benefits:**
- Single connection configuration
- Automatic TLS/authentication alignment
- Simplified user experience

### Custom Connection Mode
**How it works:**
- Separate host/port/path/TLS configuration
- User manually configures Sendspin endpoint
- Default port: 8927 (standalone Sendspin server)
- Auto-detects proxy mode if port matches main connection

**Use cases:**
- Standalone Sendspin server on different host
- Custom port configurations
- Direct connection without proxy

**Migration:**
- Existing users with custom host or port ≠ 8095 → automatically enabled
- New users → disabled (use proxy mode)

---

## Known Issues & Bugs

### High Priority
1. **Error handling incomplete** - Some edge cases not handled gracefully

### Medium Priority
2. **No codec negotiation** - Server chooses codec, client accepts
3. **Opus header parsing** - Pre-skip samples not handled (may cause click at start)

### Low Priority
4. **No logging controls** - Can't adjust log verbosity at runtime
5. **Thread priority not set** - Playback thread should be high priority

### Resolved (Shipped to TestFlight 2026-04-19+)
- ~~**iOS volume control**~~ — Reads real system volume via `AVAudioSession.outputVolume` (fixed 2026-02-20)
- ~~**iOS background playback**~~ — `AVAudioSession` interruption + route-change handlers added; auto-resumes after phone calls, Siri, or headphone disconnect (fixed 2026-02-20, in production)
- ~~**No audio on iOS (time-base mismatch)**~~ — `MessageDispatcher` and `AudioStreamManager` were using separate `TimeSource.Monotonic.markNow()` instances; `ClockSynchronizer.serverLoopOriginLocal` was calibrated in MessageDispatcher's domain but compared with AudioStreamManager's independent epoch, causing all chunks to appear perpetually early. Fixed by adding a shared `startMark` + `getCurrentTimeMicros()` to `ClockSynchronizer` and having both classes delegate to it (fixed 2026-02-20). Sync layer further refined in #261 (Kalman-smoothed clock offset, reorder buffer, wall-clock-gated playback) shipped 2026-04-24.
- ~~**iOS WebRTC stubs**~~ — Full implementation shipped 2026-04-24 (#264, #265). See `webrtc-completion-summary.md`.
- ~~**iOS Compose Metal background GPU rejection**~~ — `ComposeRenderingGuard` deterministic backstop pauses the Metal renderer on `UIScene.didEnterBackground` (#265, 2026-04-24).
- ~~**iOS Opus stereo decoding**~~ — Corrected and re-enabled as default codec (#288, 2026-04-27).
- ~~**iOS CarPlay cold-launch black screen**~~ — Fixed in #277 (2026-04-26).

---

## Testing Status

### ✅ Tested & Working
- Basic playback (start, stop)
- Pause/resume
- Seek forward/backward
- Next/previous track
- Metadata display
- Clock synchronization (±10ms RTT, <1% jitter)
- State reporting
- PCM format (16-bit, 44.1kHz, 48kHz)
- Opus format (48kHz, stereo, Android)
- Adaptive buffering (good and degraded network conditions)
- Long playback sessions (tested with real streams)
- Network interruption recovery (auto-reconnect with exponential backoff)

### ⚠️ Partially Tested
- High network latency scenarios
- Multiple format switches
- Buffer adaptation edge cases

### ❌ Not Tested
- FLAC codec (implemented but not extensively tested)
- Multiple concurrent connections
- Server restart scenarios
- Clock drift over extended periods (24+ hours)

---

## Performance Metrics

### Current Measurements (Android)
- **Startup Time:** ~1-2 seconds to connect
- **Clock Sync Offset:** ±5-20ms (GOOD quality)
- **RTT:** ~10-15ms (excellent network)
- **Jitter:** ~8-10ms (very low)
- **Buffer Size (Target):** 200-400ms (adapts to network)
- **Buffer Size (Actual):** ~5000ms (server pre-fills)
- **Audio Latency:** ~100-200ms
- **Dropped Chunks:** <1% under normal conditions, 0% with good network
- **Memory Usage:** ~10-20MB
- **CPU Usage:** ~5-10% during PCM playback, ~8-12% during Opus playback
- **Bandwidth (PCM):** ~1.5 Mbps (stereo 48kHz 16-bit)
- **Bandwidth (Opus):** ~64-128 kbps (90%+ savings)

---

## Next Steps

### Immediate
1. Add volume/mute UI controls
2. Improve error messages to user

### Short Term
3. Parse Opus codec header (OpusHead) for pre-skip handling
4. Add buffer health display (debug UI)
5. Add comprehensive error recovery

### Medium Term
6. Add codec preference settings
7. Optimize memory usage

### Long Term
10. Artwork display
11. Visualizer support
12. Advanced audio processing features
13. Multi-room synchronization improvements

---

## Code Quality

### ✅ Excellent (Recently Improved)
- **Single Responsibility Principle** ⭐
  - SendspinClient: Protocol orchestration only
  - StateReporter: Periodic state reporting
  - ReconnectionCoordinator: Recovery management
  - AudioPipeline: Interface abstraction for audio playback
  - SendspinClientFactory: Client creation logic
- **Clear separation of concerns** with dedicated components
- **Platform abstraction** (expect/actual for decoders, AudioDecoder.getOutputCodec())
- **Coroutines** for async operations with proper dispatcher usage
- **StateFlow** for reactive state management
- **Comprehensive logging** throughout
- **Industry best practices** (WebRTC NetEQ-inspired adaptive buffering)
- **Robust error handling** with categorized errors (Transient/Permanent/Degraded)
- **State machines** instead of boolean flags (StreamRecoveryState)
- **Interface abstractions** for testability (AudioPipeline)
- **Configuration objects** for clean constructors (MessageDispatcherConfig)
- **Threading documentation** explaining dispatcher usage and rationale

### ✅ Good
- Parameter reduction (MessageDispatcher: 6→3 parameters)
- Smaller, focused classes (SendspinClient reduced by ~200 lines)
- No circular dependencies
- Type-safe error categorization

### ⚠️ Needs Improvement
- Limited unit tests (newly extracted components are testable but not yet tested)
- No integration tests
- Could add more KDoc to public APIs

### ❌ Missing
- Performance profiling
- Memory leak detection
- Thread safety analysis
- Stress testing

---

## Dependencies

### Runtime
- Ktor WebSocket client
- Kotlinx Serialization
- Kotlinx Coroutines
- Kermit (logging)
- AudioTrack (Android)
- **Concentus v1.0.2** (Opus decoder - Android only)

### Platform-Specific
- Android: AudioTrack for raw PCM, Concentus for Opus decoding, MediaCodec for FLAC decoding
- iOS: AudioQueue (CoreAudio) + libFLAC (C) + swift-opus (libopus) + native PCM. MPV/MPVKit removed 2026-01-23.

---

## Critical Implementation Details

### Proxy Authentication (Token-based)
**CRITICAL:** When using proxy mode (port matches main connection), MUST send `auth` message before `client/hello`.
- Message format: `{ "type": "auth", "token": "<user_token>", "client_id": "<sendspin_id>" }`
- Wait for `auth_ok` response before proceeding
- Server timeout: 10 seconds
- Only required when `serverPort == mainConnectionPort`

### Time Base (Monotonic Time)
**CRITICAL:** Must use `System.nanoTime()` throughout, NOT `System.currentTimeMillis()`.

### State Reporting (Periodic Updates)
**CRITICAL:** Must send `client/state` with `SYNCHRONIZED` every 2 seconds during playback.

### Adaptive Buffering Behavior
- **Target Buffer** = Minimum safe buffer based on network conditions
- **Actual Buffer** = Server-managed pre-fill buffer (~5 seconds)
- These are intentionally different:
  - Target: "How much we need to prevent underruns"
  - Actual: "How much audio is queued"

---

## Lessons Learned

### Critical Discoveries
1. **Time base matters** - Must use monotonic time for sync, not wall clock
2. **State reporting is mandatory** - Server needs regular updates every 2 seconds
3. **Debugging is essential** - Comprehensive logging saved hours of debugging
4. **Binary parsing is tricky** - Endianness and byte ordering matter
5. **Opus works great** - Concentus is reliable, 90%+ bandwidth savings
6. **Adaptive buffering is complex** - But critical for varying network conditions

### Best Practices
1. Use monotonic time for all timing operations
2. Report state frequently (every 2 seconds)
3. Log extensively during development
4. Test with real server, not just mocks
5. Handle partial AudioTrack writes
6. Use industry-proven algorithms (EWMA, Kalman filter, Welford's algorithm)
7. Prevent oscillation with hysteresis and cooldowns

### Gotchas
1. `System.currentTimeMillis()` vs `System.nanoTime()` - Use nanoTime for timing
2. Server time is relative, not absolute
3. AudioTrack may not write all bytes at once
4. Clock sync needs multiple samples to be accurate
5. Buffer must be ordered by local timestamp, not server timestamp
6. Opus codec header (pre-skip) not currently parsed
7. Target buffer vs actual buffer are different concepts

---

## Documentation

### Current
- `sendspin-status.md` - **This document** - Current implementation status (maintained)
- `ios_audio_pipeline.md` - iOS MPV integration documentation
- `volume-control.md` - MediaSession volume control implementation
- `sendspin-resilient-architecture.md` - Auto-reconnect architecture design

### Historical (Archived 2026-01-16)
- ~~`sendspin-integration-design.md`~~ - Deleted (superseded by status doc)
- ~~`sendspin-integration-guide.md`~~ - Deleted (superseded by status doc)
- ~~`sendspin-android-services-integration.md`~~ - Deleted (contradictory, confusing)
- ~~`connection-service-design.md`~~ - Deleted (never implemented)

---

## Changelog

### 2026-04 - iOS Parity & Production Shipping
- ✅ **TestFlight distribution active** (#234, 2026-04-19) — iOS users now on production build cadence
- ✅ **iOS WebRTC parity** (#264, 2026-04-24) — `DataChannelWrapper.ios.kt` sends real bytes for text frames; iOS WebRTC fully functional
- ✅ **Sendspin playback sync layer** (#261, 2026-04-24) — Kalman-smoothed clock offset, reorder buffer, wall-clock-gated playback in `AudioStreamManager` + `ClockSynchronizer`
- ✅ **Compose Metal background pause** (#265, 2026-04-24) — deterministic backstop in `ComposeRenderingGuard` + `ComposeRendererBridge` prevents GPU command-buffer rejection while audio background mode keeps the run loop alive
- ✅ **CarPlay cold-launch black screen** (#277, 2026-04-26) — fixed; CarPlay UI loads reliably from head unit
- ✅ **iOS Opus stereo decoding** (#288, 2026-04-27) — corrected and re-enabled as default codec
- ✅ **WebRTC sendspin channel reuse + reauth-on-reconnect** (#293, 2026-04-28) — sendspin channel survives WebRTC reconnect cleanly; reauth path unified across Direct + WebRTC modes
- 📊 Status: iOS reached production parity with Android for streaming, WebRTC remote, background audio, and CarPlay browse. Outstanding iOS-only gap: CarPlay Siri interaction donation (see `carplay.md`).

### 2026-02-20 - iOS Bug Fixes & Audio Playback (Pending Tests)
- ✅ **iOS volume control** — `getCurrentSystemVolume()` now reads real system volume via `AVAudioSession.outputVolume` instead of returning hardcoded 100
- ✅ **iOS background audio** — Added `AVAudioSession` interruption + route-change `NotificationCenter` observers to `NativeAudioController`; auto-resumes after phone calls, Siri interruptions, or headphone disconnect
- ✅ **Efficient Kotlin→Swift PCM transfer** — Added `writeRawPcmNSData(NSData)` on `NativeAudioController`; Kotlin side uses `usePinned`/`addressOf` bulk copy instead of per-byte Swift interop loop
- ✅ **DataChannelWrapper binary fast path** — First-byte check (`{`/`[`) instead of full `decodeToString()` on every binary audio message at 50-100/sec
- ✅ **iOS no-audio bug fixed** — Root cause: `MessageDispatcher` and `AudioStreamManager` each had their own `TimeSource.Monotonic.markNow()` start mark; `ClockSynchronizer.serverLoopOriginLocal` was set in MessageDispatcher's time domain but compared against AudioStreamManager's independent epoch in the playback loop. Fix: `ClockSynchronizer` now owns the shared `startMark` and exposes `getCurrentTimeMicros()`; both classes delegate to it
- ✅ **OAuthHandler** — Opens OAuth URLs in Safari via `UIApplication.openURL` (was throwing `UnsupportedOperationException`)
- ✅ **SystemAppearance** — Sets `overrideUserInterfaceStyle` on all windows for dark/light mode support
- 📊 Status: All known iOS playback blockers resolved — awaiting device/simulator validation

### 2026-02-05 - Architecture Refactoring & Maintainability
- ✅ **SendspinClientFactory** - Extracted client creation logic from MainDataSource
  - Uses Kotlin Result<T> for error handling
  - Validates settings, builds config, creates client
  - Reduced MainDataSource complexity by ~100 lines
- ✅ **AudioPipeline interface** - Decoupled SendspinClient from AudioStreamManager
  - Interface abstraction enables testing with mocks
  - Future audio backend swapping without touching SendspinClient
- ✅ **AudioDecoder.getOutputCodec()** - Removed platform-specific coupling
  - Replaced PassthroughDecoder marker interface with explicit method
  - Android decoders return PCM, iOS decoders return passthrough codec
  - Cleaner abstraction following Open/Closed Principle
- ✅ **MessageDispatcherConfig** - Simplified constructor from 6→3 parameters
  - Groups configuration separate from dependencies
  - Easier to construct in tests
- ✅ **StateReporter** - Extracted periodic state reporting (~110 lines)
  - Manages own CoroutineScope and job lifecycle
  - Provider pattern for volume/mute/playback state
  - SendspinClient reduced by ~45 lines
- ✅ **ReconnectionCoordinator** - Extracted reconnection logic (~210 lines)
  - **StreamRecoveryState** machine replaces wasStreamingBeforeDisconnect boolean
  - Proper state: Idle → AwaitingReconnect → RecoveryInProgress → Success/Failed
  - Monitors WebSocket state, preserves buffer, handles recovery timeout
  - SendspinClient reduced by ~100 lines
- ✅ **SendspinError categorization** - Type-safe error handling
  - Transient(cause, willRetry): Auto-recoverable errors
  - Permanent(cause, userAction): Requires user intervention
  - Degraded(reason, impact): Limited functionality
  - Better UI feedback and user guidance
- ✅ **Threading documentation** - Comprehensive KDoc for AudioStreamManager
  - Documents 3-dispatcher architecture (Default, audioDispatcher, Default)
  - Explains producer-consumer pattern and rationale
  - Threading model now self-documenting
- 📊 **Overall improvements:**
  - SendspinClient reduced from ~455 to ~280 lines (-38%)
  - Better separation of concerns (Single Responsibility Principle)
  - Improved testability (interfaces, smaller components)
  - Clearer state management (state machines vs booleans)
  - Enhanced error handling (categorized errors)
- 📊 Status: Production-ready with significantly improved maintainability

### 2026-02-05 - Proxy Mode & Authentication
- ✅ **Proxy mode** - Default connection via main server (port 8095) + `/sendspin` path
- ✅ **Token authentication** - Authenticate before protocol handshake in proxy mode
- ✅ **Custom connection mode** - Optional separate host/port configuration
- ✅ **Auto-detection** - Proxy mode detected when port matches main connection
- ✅ **Migration support** - Existing users with custom configs automatically use custom mode
- ✅ **Protocol states** - Added `ProtocolState.AwaitingAuth` for auth flow
- 📊 **Default port** - Changed from 8927 to 8095 for new installs
- 📊 **UI improvements** - "Custom Sendspin Connection" toggle in settings
- 📊 Status: Simplified configuration for new users while preserving advanced options

### 2026-01-16 - iOS Support & Network Resilience
- ✅ **iOS full implementation** - MPV-based pipeline with FLAC/Opus/PCM
- ✅ **Android FLAC decoder** - MediaCodec-based with hardware acceleration
- ✅ **Auto-reconnect** - WebSocketHandler automatic reconnection with exponential backoff
- ✅ **Network resilience** - Aggressive keepalive settings for connection stability
- 📊 **Documentation cleanup** - Removed 4 outdated/contradictory design documents
- 📊 Status: Both Android and iOS platforms now have working implementations

### 2026-01-05 - Opus + Adaptive Buffering Release
- ✅ Added Opus decoder for Android (Concentus library)
- ✅ Implemented adaptive buffering (network-aware)
- ✅ Extended BufferState with adaptive metrics
- ✅ Added Opus to client capabilities
- ✅ Tested with real Music Assistant server
- 📊 Confirmed: RTT ~10ms, jitter ~9ms, 0% drops, excellent performance

### 2025-12-26 - Milestone: Basic Playback Working
- ✅ Fixed clock synchronization (monotonic time base)
- ✅ Added periodic state reporting (every 2 seconds)
- ✅ Added comprehensive debugging logs
- ✅ Verified playback, seek, pause, next/previous
- 🐛 Known issues: No volume UI, no auto-reconnect, many minor bugs

### 2025-12-24 - Initial Implementation
- ✅ Created core protocol implementation
- ✅ WebSocket connection
- ✅ Message parsing and serialization
- ✅ AudioTrack integration
- 🐛 Clock sync broken (wrong time base)
- 🐛 State reporting missing

---

**Status:** ✅ **Production** on Android and iOS with multi-codec support. iOS shipping via TestFlight since 2026-04-19.

**Platform Summary:**
- **Android**: ✅ PCM, Opus (Concentus), FLAC (MediaCodec) — full background playback & Android Auto
- **iOS**: ✅ PCM, Opus (swift-opus), FLAC (libFLAC) via AudioQueue — background playback + Control Center + CarPlay; WebRTC remote at parity with Android
