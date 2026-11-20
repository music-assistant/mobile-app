# iOS Audio Pipeline

The built-in Sendspin player on iOS writes encoded audio to a native `AudioQueue` player in Swift. Swift
decodes FLAC and Opus. The MPV player was removed.

For the player module and the Sendspin rules, see the Sendspin section of `.claude/architecture.md`.

## Chain

```
sendspin-kmp scheduler (jitter buffer, timing, drift)
  → PassThroughDecoder (IosDecoderFactory): the encoded bytes pass through
  → AudioQueueSink (composeApp iosMain, player/local/)
  → PlatformAudioPlayer (Kotlin interface), PlatformPlayerProvider.player
  → NativeAudioController.swift: decoder, PCM queue, AudioQueue
  → AudioDecoders.swift: FLACLibDecoder, OpusLibDecoder, PCMPassthroughDecoder
```

## Kotlin side

- `IosModule` binds `AudioQueueSink` and `IosDecoderFactory` for the Sendspin player.
- `IosDecoderFactory` supports every codec. `PassThroughDecoder` returns its input and reports the bit depth
  of the stream.
- `AudioQueueSink.open` calls `prepareStream` with the codec, the sample rate, the channels, the bit depth and
  the codec header (Base64). If the native player reports an error, the sink emits `SinkEvent.Died`.
- `write` copies the bytes into one `NSData` with `usePinned` and calls `writeRawPcmNSData`.
- The sink gives no position and no latency. So iOS sync is open loop, and the manual delay setting is the
  only correction.
- `pause`, `resume`, `flush` and `close` call `pauseSink`, `resumeSink`, `flush` and `stopRawPcmStream`.
- `iOSApp.swift` makes one `NativeAudioController` and sets it as `PlatformPlayerProvider.player` before
  the Kotlin graph starts.

## NativeAudioController

- `prepareStream` stops the old queue, clears the PCM queue and makes the decoder with
  `AudioDecoderFactory.create`. A codec header (FLAC STREAMINFO) goes to the decoder first.
- The first packet starts the `AudioQueue` and calls `NowPlayingCoordinator.activatePlayback`.
- Each packet is decoded, cut into whole frames of at most 64 KiB (`PCMChunker`) and added to the PCM queue
  under an `NSLock`.
- The `AudioQueue` has 5 buffers of 64 KiB (`kNumberOfBuffers`, `kBufferSize`). The callback fills a buffer
  from the PCM queue, or with silence if the queue is empty.
- `shouldPlay` is the write gate. While it is false (pause, interruption, route loss), the controller drops
  incoming packets, so a late packet cannot start the queue again.
- `pauseSink` tears the queue down instead of `AudioQueuePause`, because a paused queue plays its old buffers
  again on resume. The next packet builds a new queue.

## Bit depth

| Codec | Decoder output | AudioQueue format |
|-------|----------------|-------------------|
| FLAC (`libFLAC`) | Int32, shifted to full range | 32-bit signed integer |
| PCM 24-bit | Int32, unpacked from 3 bytes and shifted left by 8 | 32-bit signed integer |
| PCM 16-bit | Unchanged | 16-bit signed integer |
| Opus (`swift-opus`) | Int16, interleaved | 16-bit signed integer |

- `startAudioQueue` selects 32-bit if the codec is FLAC or the bit depth is 24.
- `PCMPassthroughDecoder` accepts 16-bit and 24-bit only. Another depth throws an error.
- `supportsHiResAudio` is true on iOS (`utils/HiResAudio.kt`), because the player widens 24-bit audio to 32-bit.
- `OpusLibDecoder` expects one complete Opus packet in each call.
- `FLACLibDecoder` resets its input buffer if it gets larger than 1 MiB.

## Audio session and Now Playing

- `NowPlayingCoordinator.swift` owns `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`. It renders the
  Kotlin Now Playing channels (track, transport, modes) from `KmpHelper`.
- It sets the `.playback` category at start, with no mix option, so the app can be the Now Playing app.
  `activatePlayback` activates the session when playback starts.
- A Control Center or CarPlay command goes to the handler of the open sink with source `remote`, and
  `AudioQueueSink` gives it to `LocalPlayerAdapter.onRemoteCommand`. After `close`, the handler is null and
  the command is dropped.

## Interruptions and route changes

`NativeAudioController` observes `AVAudioSession.interruptionNotification` and `routeChangeNotification`.

- Interruption `.began`: if the queue plays, the controller sends `pause` with source `interruption`.
  `AudioQueueSink` makes it `SinkEvent.FocusLost`.
- Interruption `.ended`: if the controller paused for the interruption, it sends `play`
  (`SinkEvent.FocusRegained`). It does not if `secondaryAudioShouldBeSilencedHint` is true. The controller
  does not use `.shouldResume`, because Apple gives it as a hint only.
- Route change `.oldDeviceUnavailable` (headphones, Bluetooth, CarPlay): the controller stops the queue, clears
  the PCM queue and sends `pause` with source `route_loss`. iOS sends no matching end event, so the user
  starts playback again.

## Files

| File | Purpose |
|------|---------|
| `composeApp/src/iosMain/.../player/local/AudioQueueSink.kt` | `AudioSink` over the native player, `IosDecoderFactory`, `PassThroughDecoder` |
| `composeApp/src/iosMain/.../player/PlatformAudioPlayer.kt` | `PlatformAudioPlayer`, `RemoteCommandHandler`, `PlatformPlayerProvider` |
| `composeApp/src/iosMain/.../di/IosModule.kt` | Koin bindings of the sink and the decoder factory |
| `iosApp/NativeAudioController.swift` | AudioQueue, PCM queue, interruption and route observers |
| `iosApp/AudioDecoders.swift` | `AudioDecoderFactory`, `FLACLibDecoder`, `OpusLibDecoder`, `PCMPassthroughDecoder` |
| `iosApp/PCMChunker.swift` | Cuts PCM into whole frames that fit one AudioQueue buffer |
| `iosApp/NowPlayingCoordinator.swift` | Audio session category and activation, Now Playing info, remote commands |
| `iosApp/NowPlayingInfoStore.swift` | The one writer of `nowPlayingInfo` |
| `iosApp/iosApp/iOSApp.swift` | Makes the controller and registers it with `PlatformPlayerProvider` |
