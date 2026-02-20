# iOS Audio Pipeline & Native AudioQueue Integration

## Overview
High-Performance Audio Pipeline for iOS using native `AudioQueue` services and optimized C/Swift decoders for FLAC/Opus/PCM streaming playback via the Sendspin protocol. This implementation replaces the previous MPV-based solution to provide better system integration and lower overhead.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Server                                   │
│  Sends FLAC/Opus/PCM chunks + codec_header via WebSocket        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   AudioStreamManager (Kotlin)                    │
│  • Receives chunks with timestamps                               │
│  • Buffers/reorders (TimestampOrderedBuffer, AdaptiveBuffer)    │
│  • PassthroughDecoder for iOS (wraps codec type)                │
│  • Calls writeRawPcm(data) to push encoded bytes                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│            MediaPlayerController.ios.kt → PlatformPlayerProvider │
│  Delegates to Swift via PlatformAudioPlayer interface           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  NativeAudioController.swift (Swift)             │
│  • prepareStream(codec, sampleRate, channels, bitDepth, header) │
│  • Manages AudioQueue lifecycle                                 │
│  • Maintains PCM buffer (PCMData[])                             │
│  • Handles remote commands (via NowPlayingManager)              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  AudioDecoders.swift (Swift)                     │
│  • Factory creates specific decoder for stream                   │
│  • Decodes input data -> Int16 PCM for AudioQueue               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     AudioQueue (iOS Core Audio)                  │
│  • 5x 64KB buffers                                               │
│  • Callback-driven playback                                     │
│  • System audio session management                              │
└─────────────────────────────────────────────────────────────────┘
```

## Key Implementation Details

### Codec Support

Per-platform codec support is defined via `Codecs.list`. The iOS implementation uses specific libraries for each codec:

| Codec | Implementation Source | decoding Details |
|-------|-----------------------|------------------|
| **FLAC** | `libFLAC` (C library) | `FLACLibDecoder` uses callbacks to decode to Int16 PCM. Handles 16/24-bit input (downsamples 24->16). |
| **Opus** | `swift-opus` (libopus) | `OpusLibDecoder` decodes to Float32, then converts to Int16. |
| **PCM** | Native | `PCMPassthroughDecoder` handles 16/24/32-bit integer PCM. |

### AudioQueue Management

The `NativeAudioController` manages an `AudioQueueRef` directly:

1.  **Buffering**: Uses 5 buffers of 64KB each to ensure smooth playback and minimize stuttering.
2.  **Format**: Always configures AudioQueue for Linear PCM, 16/24/32-bit signed integer, based on the source stream bit depth.
3.  **Synchronization**: Uses `NSLock` to protect the PCM buffer shared between the writer (Kotlin->Swift) and the specific reader callback (AudioQueue).

### Critical Implementation Notes

1.  **Decoding in Swift**: Unlike the previous MPV implementation where the player handled everything, we now explicitly decode encoded chunks (FLAC/Opus) into PCM *before* feeding them to the AudioQueue. This happens in `NativeAudioController.writeRawPcm`.

2.  **Codec Header**: The `codec_header` (e.g., FLAC STREAMINFO) is passed to `prepareStream`. `nativeAudioController` uses this to initialize the decoder (specifically for FLAC).

3.  **Now Playing Integration**: `NowPlayingManager` is integrated directly into the controller to handle Lock Screen / Control Center metadata and commands.

4.  **Bit Depth Handling**:
    - **FLAC**: 24-bit inputs are currently shifted down to 16-bit for playback compatibility if needed, or handled as is depending on decoder logic.
    - **PCM**: Custom unpacking logic supports 24-bit packed integers (3 bytes per sample) which standard `CoreAudio` types don't always handle automatically.

## Files

| File | Purpose |
|------|---------|
| `iosApp/NativeAudioController.swift` | Main implementation. Manages AudioQueue, referencing Decoders. |
| `iosApp/AudioDecoders.swift` | Contains `FLACLibDecoder`, `OpusLibDecoder`, `PCMPassthroughDecoder` and factory. |
| `iosApp/NowPlayingManager.swift` | Handles MPNowPlayingInfoCenter and MPRemoteCommandCenter. |
| `composeApp/.../MediaPlayerController.ios.kt` | Kotlin stub that delegates to `PlatformPlayerProvider`. |
| `composeApp/.../utils/Codecs.ios.kt` | Supported codecs: FLAC, Opus, PCM. |

## Status: ✅ Working (updated 2026-02-20)

- [x] FLAC streaming playback (via libFLAC)
- [x] Opus streaming playback (via swift-opus)
- [x] PCM streaming playback (16/24/32 bit)
- [x] Proper codec header handling
- [x] Native AudioQueue implementation (replacing MPV)
- [x] Background audio: `AVAudioSession` interruption + route-change handlers — resumes after phone calls, Siri, headphone disconnect (completed 2026-02-20, pending tests)
- [x] Efficient Kotlin→Swift data transfer via `writeRawPcmNSData(NSData)` using `usePinned` bulk copy (completed 2026-02-20)
- [x] Lock Screen / Control Center: Now Playing info + remote commands via `NowPlayingManager`

## Known Issues

1. **Seek/Scrub**: Scrubbing might trigger issues in the API layer if not handled gracefully.
2. **Resumption**: Stream resumption during network changes relies on the existing Sendspin resilience strategy (buffer preservation).

## Future Work

1. **Sample-Accurate Sync**: Direct access to `AudioQueue` callbacks provides better timing info than MPV. Could implement precise synchronized playback using `mSampleTime` from `AudioQueueTimeline`.
