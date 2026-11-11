# Volume Control

**Last reviewed:** 2026-04-28

## Design intent (mobile)

The mobile app does **not** attempt bidirectional system-volume sync between the device and the Music Assistant server. We rely on the OS-native audio paths instead:

- The user's hardware volume buttons control the device's system audio output directly (OS-level), as on any other audio app.
- The server can push a volume value to the player at the audio-path layer (app-level mixer gain), and the app applies it.
- These two paths are orthogonal; final output ≈ OS gain × app gain.

Rationale: continuous device→server volume reporting on mobile (especially with hardware-button changes) introduces debounce/echo bugs that aren't worth solving for a phone-as-player experience where users expect the hardware buttons to "just work" against the OS.

## Built-in player (this device acting as a Music Assistant player)

| Path | Implementation |
|---|---|
| Server → device gain | Server sends `{"type":"server/command","payload":{"player":{"command":"volume","volume":<0-100>}}}` → `SendspinClient.handleServerCommand()` calls `mediaPlayerController.setVolume(n)` |
| Android audio-path gain | `MediaPlayerController.android.kt` `setVolume()` → `AudioTrack.setVolume(n/100f)` |
| iOS audio-path gain | `MediaPlayerController.ios.kt` `setVolume()` → `NativeAudioController.setVolume()` → `AudioQueueSetParameter(queue, kAudioQueueParam_Volume, n/100)` |
| Server → device mute | Same protocol (`"command":"mute","mute":<bool>`); each platform applies via the same audio-path API (mute = gain 0) |
| Device → server | **Not wired.** No platform observes hardware-volume changes; nothing gets reported back. |
| In-app volume slider | **None, by design.** Hardware buttons are the user-facing control. |

There is also a `MediaPlayerController.getCurrentSystemVolume(): Int` `expect` declaration. It is currently called once at construction and once on each `connectWithTransport()` in `SendspinClient`, but the value is only used for a startup log line — it does not flow into the protocol or into any reader. (Flagged as candidate for a future dead-code cleanup pass; not a behavioural concern.)

## Other players (this device controlling a different player on the network)

The MA player model exposes `canSetVolume`, `volumeLevel`, `groupVolume`, etc. on each `Player`. The mobile app **does** show an in-app slider for those:

- `PlayersPager.kt:344-391` — pager-card slider on the Now Playing carousel
- `SelectPlayerDialog.kt:509-544` — slider in the player-picker dialog

Visibility gate: `Player.isVolumeSliderAccessible = (isGroup || canSetVolume) && currentVolume != null`. When the slider moves, the app sends `PlayerAction.VolumeSet(value)` (or `GroupVolumeSet(value)` for groups) over the main MA connection — that path is unrelated to the Sendspin built-in-player path described above.

## Hardware volume keys for the selected remote player (Android, opt-in)

The setting "Volume buttons control the selected player" (`SettingsRepository.volumeKeysControlSelectedPlayer`) is off by default. The settings screen shows it only where `supportsVolumeKeyRouting` is true, and that is Android only. iOS does not let an app change the function of the volume buttons (App Review guideline 2.5.9).

Rule: with the setting on and a remote player selected, the volume keys change the volume of that player while the app is on screen. They never change the device volume in this case. All other cases keep the device volume.

- `MainActivity.onKeyDown` and `onKeyUp` get the volume keys only while the activity has window focus. So the home screen, the lock screen, a switched-off screen and other apps keep the device volume. The app does not track the lifecycle for this.
- `VolumeButtonService.onVolumeKey` takes a key if the setting is on and the selected player is not the local player. If the player cannot act on the key, the service sends no step and emits a `VolumeKeyRefusal`, once for each press and not for its repeats. `MainNavRoot` shows the reason as a toast: no connection to the server, the player is off, or the player has no volume control.
- While the setting is off, the device volume changes, and the "Volume buttons only work for the local player" toast shows for a remote player.
- If the activity takes the `ACTION_DOWN` of a key, it also takes the repeats and the `ACTION_UP` of that key. The activity does not take the keys during a call or VoIP (`AudioManager.mode` is not `MODE_NORMAL`).
- The command is the same command as the taps beside the slider: `volume_up` or `volume_down`, or `group_volume_up` or `group_volume_down` if `PlayerData.usesGroupVolume` is true. The server applies its own volume step.
- A held key sends one step for each server round trip. One step is in flight, and only the latest press waits (a conflated channel). A press in the other direction replaces the waiting step.
- The media session does not change. It stays `setPlaybackToLocal(STREAM_MUSIC)`, because the session exists only while a playback service holds it, and it follows `nowPlayingPlayer` and the car isolation, not the selected player.
- The system volume panel does not show. `RemoteVolumeOverlay` shows the player name and its volume from the player state, and it hides 1.5 s after the last press. It does not show while the player is expanded, because the expanded player shows the name and the volume.

## Platform parity

Both platforms behave the same way today:

| Path | Android | iOS |
|---|---|---|
| Server-pushed volume → audio output | ✅ via `AudioTrack.setVolume` | ✅ via `AudioQueueSetParameter` |
| Hardware buttons → OS system volume | ✅ (handled by OS) | ✅ (handled by OS) |
| Hardware buttons → server reporting | ❌ not wired | ❌ not wired |
| `getCurrentSystemVolume()` returns real value | ✅ reads `AudioManager.STREAM_MUSIC` | ⚠️ stubbed to `100` (inconsequential — only feeds a log line) |
| In-app slider for built-in player | ❌ by design | ❌ by design |
| In-app slider for other players on network | ✅ | ✅ (confirmed working on TestFlight 2026-04-28) |

Effectively at parity. The iOS `getCurrentSystemVolume()` stub is a cosmetic gap, not a functional one.

## Historical note

A previous version of this document described a `MediaSessionHelper` + `ContentObserver` design for Android with continuous bidirectional sync. That code is not present in the current Sendspin-based codebase — it appears to have described an earlier ExoPlayer-era integration that was replaced. The bidirectional-sync intent was deliberately not carried forward.
