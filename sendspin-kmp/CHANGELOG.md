# Changelog

All changes to `io.github.music-assistant:sendspin-kmp` are in this file.

## Unreleased

- The source send queue holds 26 chunks, 500 ms of 20 ms PCM, instead of 32. More would only
  hold audio that the 500 ms age check drops anyway. A FLAC chunk is longer, so for FLAC the age
  check alone bounds the queue, as before.
- Source signal: under both dialects, the source now reports `client/state.source.signal =
  "absent"` after 5 seconds of silence in a stream (every sample at or below about -70 dBFS), and
  `"present"` on the first block above it. The server can then stop a silent source (Music
  Assistant does after 60 seconds). `signal` is a required field of the source state in the
  current spec, so a constant `"present"` on a silent input did not conform. `RC1` still advertises
  `line_sense` in `client/hello`, which the current spec removed.
- New `SourceSignal` (`Present`, `Absent`) and `PlayerState.Connected.sourceSignal`, the signal that
  the source last reported. The parameter has a default value, so the source stays compatible.
- `SourceStatus.Unpaired`: the server activated an unpaired session, which never gets the source
  role. Before, that session gave `SourceStatus.Off`, the same as no answer. A `when` over
  `SourceStatus` without an `else` branch needs the new case.

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
