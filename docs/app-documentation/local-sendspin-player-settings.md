# Setting up the Local Player (optional)

You can use the app purely as a remote control for external players, with the local player disabled. Or you can configure it as a Sendspin client to play music directly through your device.

## Enabling the Local Player

In **Settings**, scroll to the **Sendspin Player** section. Use the switch in the section header to turn the player on or off.

You can change these settings when the player is on or off:

- **Player name** — The name that shows in Music Assistant (default: My Phone).
- **Codec preference** — The audio codec for the stream. See [Codec Preference](#codec-preference) below.
- **24-bit audio** — Streams 24-bit instead of 16-bit audio. This uses about 50% more bandwidth. It is available only with FLAC or PCM. See [24-bit Audio](#24-bit-audio).
- **Advanced Configuration** — Tap this row to show or hide the settings that follow:
  - **Custom Sendspin connection** — Keep this setting off if your setup does not need a custom connection. See [Custom Sendspin Connection](#custom-sendspin-connection).
  - **Buffer size** — The maximum buffer size for the current stream, set with a slider (default: 10 s). The buffer helps the player continue through short network interruptions. It is not for offline playback, and it does not load the next queue items in advance. Larger values can prevent issues on an unreliable connection, but a larger buffer uses more app memory. The slider shows the approximate memory the buffer needs for the selected length, codec and bit depth (for example, about 1.9 MB at 10 s with FLAC, or about 0.5 MB at 30 s with Opus).

The **Advanced Configuration** opens automatically if one of its settings is not at the default value.

## Saving Changes

Tap **Save changes** to apply your changes. Tap **Reset to defaults** to apply the default values. Both buttons are only active when there is something to save or reset.

If the player is playing audio, the app shows an **Interrupting Playback** dialog first. Saving changes to the player interrupts the current playback and clears the current queue, so tap **Continue** to proceed or **Cancel** to keep playing.

![Local Player Disabled - Setup](screenshots/local-sendspin-player-settings/local-player-disabled.jpeg)
![Local Player Advanced Configuration](screenshots/local-sendspin-player-settings/local-player-advanced-configuration.jpeg)
![Interrupting Playback Confirmation](screenshots/local-sendspin-player-settings/interrupting-playback.jpeg)

> **Good to know:** The Local Player is a private player. It plays music on your device while you use the app. Other Music Assistant players, for example speakers, stay on and available all the time. The Local Player does not work that way. So, by default, the server hides it from other apps and devices.

> It's hidden by default in the Music Assistant Web UI, but visible under Music Assistant > Settings > Players as long as it's registered to the server. Registration only happens automatically while the Local Player is enabled and the app runs with a server configured. When the system stops the app, the player is de-registered and its settings become uneditable.

> **Note:** if you un-hide the player in the Web UI, you can start playback on it or group it from there, and the app shows that playback like any other. While the app is in the background the system may stop it at any time, and the playback ends with it.

## Codec Preference

The codec controls how audio is compressed and streamed from your Music Assistant server to the app. Choose based on your network conditions and how much audio quality matters to you. The selected option shows a short description underneath (for example, "lossless, medium bandwidth").

- **Opus** *(Compressed, lowest bandwidth)* — A lossy format that uses the least data. Audio quality is good but not lossless. Best for mobile data connections, slower Wi-Fi, or when bandwidth is a concern. 24-bit audio is not available with Opus.

- **FLAC** *(Lossless, medium bandwidth)* — Streams audio in full quality without any loss, using a moderate amount of bandwidth. Recommended over PCM for most setups — identical quality to PCM, with less network load.

- **PCM** *(Lossless, high bandwidth)* — Streams uncompressed, lossless audio but uses considerably more bandwidth than FLAC for identical quality. Only use this if your device or firmware has trouble playing FLAC files.

## 24-bit Audio

Turn on **24-bit audio** to stream at 24-bit instead of 16-bit depth. This uses about 50% more bandwidth than 16-bit audio, and it also increases the memory needed for the buffer.

This option is available only with the **FLAC** and **PCM** codecs. If you select **Opus**, the switch is disabled and shows "Available with FLAC or PCM".

On Android, 24-bit audio needs Android 12 or later. On an older Android version, the switch is disabled and shows "Needs Android 12 or newer". All iOS versions support 24-bit audio.

## Custom Sendspin Connection

If the app cannot reach your Music Assistant server with the default connection, open **Advanced Configuration** and turn on **Custom Sendspin connection**. Then set the connection manually. While the setting is off, the fields are disabled and the app uses the default connection.

| Field | Description |
|---|---|
| **ws:// or wss://** | Select **wss://** if your server uses a secure WebSocket connection (TLS) |
| **Host** | The hostname or IP address of your Music Assistant server. If empty, the app uses the server host. |
| **Port** | Port number (default: `8095`). If empty, the app uses the default. |
| **Path** | Sendspin endpoint path (default: `/sendspin`) |
| **Sign in through Music Assistant** | On by default. The app sends your Music Assistant sign-in to the custom server. Turn it off if the custom connection goes to a plain Sendspin server instead of Music Assistant. |

![Custom Sendspin Connection](screenshots/local-sendspin-player-settings/custom-sendspin-connection.jpeg)

## Car (Android Auto / CarPlay)

Once the Local Sendspin Player is enabled, the **Car (Android Auto / CarPlay)** section becomes available below the player settings. Here you can configure how the app behaves when used in Android Auto or Apple CarPlay:

- **Tabs** — Customize which tabs are shown and in what order they appear.
- **Item actions** — Set the default action performed when tapping different item types.
- **DSP presets** — Configure what happens to the DSP for the Local Player when the phone is connected to or disconnected from the car.

For Android Auto, refer to [Android Auto](android-auto.md#car-settings).

After setting up the Local Player, you can [start using the app](home.md).