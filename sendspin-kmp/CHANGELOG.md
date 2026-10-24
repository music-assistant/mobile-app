# Changelog

All changes to `io.github.music-assistant:sendspin-kmp` are in this file.

## 0.2.0

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
