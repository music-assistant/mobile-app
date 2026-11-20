# Volume Control

The app does not sync the device volume with the server. The hardware volume keys change the device volume,
with one exception: the opt-in volume key routing on Android (see below).

## Built-in player (local Sendspin player)

- The Sendspin player advertises no player commands (`supportedCommands = emptyList()` in
  `SendspinPlayerImpl.sessionConfig`). So the server sends no volume or mute command to the device.
- `LocalPlayerAdapter` builds the local player entry with `canSetVolume = false` and no volume level.
  So the app shows no volume slider for the local player.
- The device volume is the only volume control. The user changes it with the hardware keys.
- `AudioTrackSink` (Android) changes the track gain for audio focus only: `DUCK_GAIN` (0.2) on a duck,
  0 on a focus loss, and 1 on a focus gain.
- `PlatformAudioPlayer.setVolume` and `setMuted` exist on iOS (`NativeAudioController`), but no Kotlin
  code calls them.

## Other players

- `Player.isVolumeSliderAccessible` gates the slider:
  `(isGroup || canSetVolume) && currentVolume != null && !isPoweredOff`.
- The sliders are in the player card of `PlayersPager` and in the group sheet (`GroupSettings`).
- A slider sends `PlayerAction.VolumeSet`, or `GroupVolumeSet` for a group. The step buttons send
  `VolumeUp`, `VolumeDown`, `GroupVolumeUp` or `GroupVolumeDown`. The commands go to the server on the MA
  connection, through `MainDataSource`.

## Media session (Android)

- `SharedMediaSessionManager` calls `setPlaybackToLocal(AudioManager.STREAM_MUSIC)`. The session has no
  remote volume provider. So the volume keys and the system volume panel change the device volume.

## Hardware volume keys for the selected remote player (Android, opt-in)

The setting "Volume buttons control the selected player" (`SettingsRepository.volumeKeysControlSelectedPlayer`)
is off by default. The settings screen shows it only where `supportsVolumeKeyRouting` is true, and that is
Android only. iOS does not let an app change the function of the volume buttons (App Review guideline 2.5.9).

Rule: with the setting on and a remote player selected, the volume keys change the volume of that player while
the app is on screen. They never change the device volume in this case. All other cases keep the device volume.

- `MainActivity.onKeyDown` and `onKeyUp` get the volume keys only while the activity has window focus. So the
  home screen, the lock screen, a switched-off screen and other apps keep the device volume. The app does not
  track the lifecycle for this.
- `VolumeButtonService.onVolumeKey` takes a key if the setting is on and the selected player is not the local
  player. `RemoteVolumeTarget` (`MainDataSource`) gives the selected player and sends the step.
- If the player cannot act on the key, the service sends no step and emits a `VolumeKeyRefusal`, once for each
  press and not for its repeats. `MainNavRoot` shows the reason as a toast: no connection to the server, the
  player is off, or the player has no volume control.
- If the activity takes the `ACTION_DOWN` of a key, it also takes the repeats and the `ACTION_UP` of that key.
  The activity does not take the keys during a call or VoIP (`AudioManager.mode` is not `MODE_NORMAL`).
- The command is the same command as the step buttons beside the slider: `volume_up` or `volume_down`, or
  `group_volume_up` or `group_volume_down` if `PlayerData.usesGroupVolume` is true. The server applies its own
  volume step.
- A held key sends one step for each server round trip. One step is in flight, and only the latest press waits
  (a conflated channel). A press in the other direction replaces the waiting step.
- The media session does not change. It stays local, because the session exists only while a playback service
  holds it. It follows `nowPlayingPlayer` and the car isolation, not the selected player.
- The system volume panel does not show. `RemoteVolumeOverlay` shows the player name and its volume from the
  player state. It hides 1.5 s after the last press (`REMOTE_VOLUME_OVERLAY_MS`). It does not show while the
  player is expanded, because the expanded player shows the name and the volume.

## Hint for a remote player

If a volume key changes the device volume while a remote player is selected, `MainNavRoot` shows the toast
"Volume buttons only work for the local player" (`players_remote_volume_hint`).

- Android: `MainActivity` calls `VolumeButtonService.onPlatformVolumeButtonPressed` for each key that it does
  not take, once for each press.
- iOS: `SystemVolumeButtonObserver` (`iosApp/iosApp/iOSApp.swift`) observes `AVAudioSession.outputVolume`
  while the app is active, and calls `KmpHelper.onPlatformVolumeButtonPressed`. The observation needs an active
  audio session. If the local player does not own the session, the observer activates it with `.mixWithOthers`,
  so it does not stop the audio of another app.
