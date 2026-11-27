# Changelog

All changes to `io.github.music-assistant:sendspin-kmp` are in this file.

## 0.2.0

### Changes that need action

- `SendspinPlayer` has the new abstract function `checkConnection()`. Add it to each
  `SendspinPlayer` implementation outside the library, for example a test fake.
- Constructor changes: `SendspinDeps` has the new parameter `encoders`, `LocalPlayerConfig` has
  `source`, and `PlayerState.Connected` has `source` and `sourceSignal`. Each parameter has a
  default value, so the source stays compatible. Compile again the code that was compiled
  against 0.1.0.
- `SourceStatus` and `SourceSignal` are new enums. A `when` over them without an `else` branch
  needs a case for each entry.

### Source role

- Sendspin `source@v1` role: the client can send audio to the server. Set
  `LocalPlayerConfig.source` to a `SourceConfig` to offer the role. A null value offers the
  `player@v1` role only. `PlayerState.Connected.source` gives the `SourceStatus`.
- `SourceDialect.SPEC` is the current spec shape. `SourceDialect.RC1` is the shape that
  aiosendspin 10.3.0 and older need. `RC1` also advertises `line_sense` in `client/hello`,
  which the current spec removed.
- `SourceStatus.Unpaired`: the server activated an unpaired session, which never gets the
  source role. `SourceStatus.Off` means that the server did not answer.
- Source signal: under both dialects, the source reports `client/state.source.signal =
  "absent"` after 5 seconds of silence in a stream (every sample at or below about -70 dBFS).
  It reports `"present"` on the first block above that level. The server can then stop a silent
  source. Music Assistant stops it after 60 seconds. `PlayerState.Connected.sourceSignal`
  (`SourceSignal.Present` or `SourceSignal.Absent`) gives the signal that the source last
  reported.
- New ports: `AudioCapture` (with `CaptureFormat` and `CapturedAudio`), and `EncoderFactory`
  with `AudioEncoder` and `EncodedAudio`. Without an `EncoderFactory`, the source sends PCM.
- The source send queue holds 26 chunks at most, which is 500 ms of 20 ms PCM. The sender drops
  a chunk that is older than 500 ms. A FLAC chunk is longer, so for FLAC the age check alone
  bounds the queue.
- Android: `AndroidEncoderFactory` in `io.music_assistant.sendspin.android`, which has a FLAC
  encoder. The encoder keeps the capture time of each chunk after a capture stall, so the
  sender does not drop the later chunks as too old.

### Player

- `SendspinPlayer.checkConnection()` asks for a connection check, for example when the app
  comes to the foreground. A waiting reconnect starts at once from attempt 0. A connected player
  sends a clock probe burst at once, and repeats a silent burst with no wait. So a dead link
  ends after 3 probe timeouts. Without a check, the stall tolerance does not change.
- FLAC and PCM also advertise 44.1 kHz, after the 48 kHz formats. From Music Assistant server
  2.10.6, the server selects the rate of the source, so 44.1 kHz audio comes without a resample.
  An older server sends the first format (48 kHz). Opus stays at 48 kHz.
- A `stream/start` during an active stream continues the same timeline, as the spec says. The
  player keeps the buffered chunks, and each chunk keeps the format that it came with. The
  scheduler changes the decoder at the first chunk of a new format. It builds a new sink only
  when the output format changes, after the old audio was heard.
- A `stream/clear` with `from_timestamp` removes only the buffered chunks at or after that time.
  Playback continues, and the player does not flush the sink or report a stop.
- The player ignores a `stream/clear` or a `stream/end` with a `roles` list that does not
  contain `player`.
- `WireVersion.SPEC_1_0` always offers and accepts the Pairing PSK, as spec 1.0.0-rc1 says. The
  stored `pairing_psk_enabled` flag applies to `WireVersion.LEGACY_9` only.
- Clock: the receive time of a `server/time` reply is the time when the transport delivers the
  frame, before the inbound queue and the decryption. Before, a reply behind an audio burst got
  a later time, and that made `max_error` larger. `Frame.Binary.receivedAtMicros` carries the
  time. A `SendspinTransport` from `Endpoint.WebRtc.openChannel` must set it with the player
  clock. A frame without it gets the time when the session reads it.
- Android: BouncyCastle gives X25519 and ChaCha20-Poly1305 on all Android levels. Android 9
  does not have the `ChaCha20-Poly1305` cipher name, so the session failed there.
- Android: `connectivityOnline` ignores a late `onLost` of the previous default network. Before,
  that event made the player offline after a network change.
- Logs: a drift summary once a minute under the tag `Sendspin.Drift`, and a line when a connect
  attempt becomes active and when a reconnect continues a stream.

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
