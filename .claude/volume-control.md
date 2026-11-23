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
- `VolumeButtonService.onVolumeKey` takes a key if the setting is on, the selected player is not the local
  player, and no call holds the audio (`inCall`, from `MainActivity`). `RemoteVolumeTarget` (`MainDataSource`)
  gives the selected player and sends the step. The same function decides why it does not take a key.
- If the player cannot act on the key, the service sends no step and emits a `VolumeKeyRefusal`, once for each
  press and not for its repeats. `MainNavRoot` shows the reason as a toast: no connection to the server, the
  player is off, or the player has no volume control.
- If the activity takes the `ACTION_DOWN` of a key, it also takes the repeats and the `ACTION_UP` of that key.
  The service does not take the keys during a call or VoIP (`MainActivity` passes `inCall` when
  `AudioManager.mode` is not `MODE_NORMAL`).
- The command is the same command as the step buttons beside the slider: `volume_up` or `volume_down`, or
  `group_volume_up` or `group_volume_down` if `PlayerData.usesGroupVolume` is true. The server applies its own
  volume step.
- A held key sends one step for each server round trip. One step is in flight, and only the latest press waits
  (a conflated channel). A press in the other direction replaces the waiting step.
- The shown level runs ahead of the reported one. The player reports its new level only after the server reached
  it, so `VolumeButtonService.pendingVolumes` holds the level that the sent steps go to, by player id, and
  `HomeScreenViewModel` puts it in the players state (`withPendingVolumes`). The slider and `RemoteVolumeOverlay`
  show it.
  - The send loop advances it right before each step leaves, not on each press, so it never counts a press that
    the channel replaced. It steps with the server's default steps (`nextVolume`: 1, 2 or 3 by range). A player
    with its own `volume_step` shows the server's level once the pending level expires.
  - It expires `PENDING_VOLUME_MS` (2 s) after the server answers the last step, like the server's
    `VOLUME_TARGET_EXPIRY`, so a slow round trip can't expire it while the step is in flight. An expiry removes
    only the level of its own step. A step the server didn't take removes the level at once:
    `sendRemotePlayerAction` returns false for no answer and for an `error_code` answer.
- The media session does not change. It stays local, because the session exists only while a playback service
  holds it. It follows `nowPlayingPlayer` and the car isolation, not the selected player.
- The system volume panel does not show. `RemoteVolumeOverlay` shows the player name and its volume from the
  player state. It hides 1.5 s after the last press (`REMOTE_VOLUME_OVERLAY_MS`). It does not show while the
  player is expanded, because the expanded player shows the name and the volume.

## Hint for a remote player

If a volume key changes the device volume while the shown player is remote, `MainNavRoot` shows a toast with the
reason. `VolumeButtonService.buttonPresses` emits an `UnroutedVolumeKey` for each such press (on Android, once for
each press and not for its repeats).

- Android: `VolumeButtonService.onVolumeKey` returns false for a key it does not take, and gives the reason:
  - `IN_CALL`: a call or VoIP holds the audio mode. Toast `volume_keys_in_call`.
  - `SETTING_OFF`: "Volume buttons control the selected player" is off. Toast `volume_keys_setting_off` (long),
    which names the shown player and the setting (`settings_volume_keys_control_player`).
  - No remote player in the key path (the local player or none): no reason and no toast. The user isn't looking
    at a remote player, or the UI is a moment ahead of `MainDataSource`.
  - Each such press logs `Volume key left to the device: <reason>, selected=<id> local=<bool>`, at info level
    when a remote player is selected and at debug level otherwise.
- iOS: `SystemVolumeButtonObserver` (`iosApp/iosApp/iOSApp.swift`) observes `AVAudioSession.outputVolume`
  while the app is active, and calls `KmpHelper.onPlatformVolumeButtonPressed`. That emits `NOT_SUPPORTED`, and
  the toast stays "Volume buttons only work for the local player" (`players_remote_volume_hint`), because iOS
  can't route the keys. The observation needs an active audio session. If the local player does not own the
  session, the observer activates it with `.mixWithOthers`, so it does not stop the audio of another app.
