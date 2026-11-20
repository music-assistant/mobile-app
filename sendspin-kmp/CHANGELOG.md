# Changelog

All changes to `io.github.music-assistant:sendspin-kmp` are in this file.

## 0.2.0

- Sendspin `source@v1` role: the client can send audio to the server. Set
  `LocalPlayerConfig.source` to a `SourceConfig` to offer the role. A null value offers the
  `player@v1` role only. `PlayerState.Connected.source` gives the `SourceStatus`.
- `SourceDialect.SPEC` is the current spec shape. `SourceDialect.RC1` is the shape that
  aiosendspin 10.3.0 and older need.
- New ports: `AudioCapture` (with `CaptureFormat` and `CapturedAudio`), and `EncoderFactory`
  with `AudioEncoder` and `EncodedAudio`. Without an `EncoderFactory`, the source sends PCM.
- Android: `AndroidEncoderFactory` in `io.music_assistant.sendspin.android`, which has a FLAC
  encoder.
- Constructor changes: `SendspinDeps` has the new parameter `encoders`, `LocalPlayerConfig` has
  `source`, and `PlayerState.Connected` has `source`. Each parameter has a default value, so
  the source stays compatible. Code that was compiled against 0.1.0 must be compiled again.

## 0.1.0

The first published version.

- Sendspin `player@v1` client with an encrypted (Noise) session, pairing, clock synchronization,
  and scheduled audio output.
- Two wire versions: `WireVersion.LEGACY_9` (aiosendspin 9.x) and `WireVersion.SPEC_1_0`
  (Sendspin spec 1.0.0-rc1, aiosendspin 10.x).
- Android ports in `io.music_assistant.sendspin.android`: `AudioTrackSink`,
  `AndroidDecoderFactory`, `SharedPreferencesSendspinKeyStore`, `connectivityOnline`,
  `sendspinAudioDispatcher`, and `ElapsedRealtimeClock`.
- Targets: Android (minSdk 28), iosArm64, and iosSimulatorArm64.
