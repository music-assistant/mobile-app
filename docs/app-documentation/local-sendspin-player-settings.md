# Setting up the Local Player (optional)

You can use the app purely as a remote control for external players, with the local player disabled. Or you can configure it as a Sendspin client to play music directly through your device.

## Enabling the Local Player

In **Settings**, scroll to the **Sendspin Player** section. Use the switch in the section header to turn the player on or off.

You can change these settings when the player is on or off:

- **Player name** — The name that shows in Music Assistant (default: My Phone).
- **Codec preference** — The audio codec for the stream. See [Codec Preference](#codec-preference) below.
- **Advanced configuration** — Tap this row to show or hide the settings that follow:
  - **Custom Sendspin connection** — Keep this setting off if your setup does not need a custom connection. See [Custom Sendspin Connection](#custom-sendspin-connection).
  - **Buffer size** — The maximum buffer size for the current stream. The buffer helps the player continue through short network interruptions. It is not for offline playback, and it does not load the next queue items in advance.

The advanced configuration opens automatically if one of its settings is not at the default value.

## Saving Changes

When the player is off, the app saves each change immediately.

When the player is on, the app keeps your changes until you tap **Save changes**. The **Save changes** button is available only if your changes are different from the saved settings. A setting that you change and then change back does not count as a change. Host, port and path do not count if **Custom Sendspin connection** is off.

Tap **Reset to defaults** to change all the settings in this section back to the default values. When the player is on, the app applies the defaults immediately.

Save and reset restart the player. If the player plays audio, the app shows the **Interrupting Playback** dialog first:

- Tap **Continue** to apply the settings. Playback stops.
- Tap **Cancel** to keep the current settings. Your changes stay on the screen, so you can save them later.

If you turn off the player while you have changes that are not saved, the app saves them.

![Local Player Disabled - Setup](screenshots/local-sendspin-player-settings/local-player-disabled.jpeg)
![Local Player Codec Preference](screenshots/local-sendspin-player-settings/local-player-codec.jpeg)
![Local Player Enabled](screenshots/local-sendspin-player-settings/player-enabled.jpeg)

> **Good to know:** The app's built-in Local Player is designed to be a private player for a personal music experience. It plays music on your device, for you, while you're using it. This is different from the other players Music Assistant supports (like speakers), which stay on and available all the time. The Local Player isn't meant to work that way, so you can't find or control it from other apps or devices.

> It's hidden by default in the Music Assistant Web UI, but visible under Music Assistant > Settings > Players as long as it's registered to the server. Registration only happens automatically while the Local Player is enabled and the app is active and connected to the server. As soon as the app goes inactive or loses that connection, the player is de-registered and its settings become uneditable.

> **Note:** de-registration can take a moment after the app becomes inactive or loses its connection. This delay is one of the reasons the player is hidden by default on other interfaces: during that window, manual actions taken from those interfaces (like the Web UI or another device) may not come through correctly in the app, even though the player might still appear active there.

## Codec Preference

The codec controls how audio is compressed and streamed from your Music Assistant server to the app. Choose based on your network conditions and how much audio quality matters to you.

- **Opus** *(Compressed, lowest bandwidth)* — A lossy format that uses the least data. Audio quality is good but not lossless. Best for mobile data connections, slower Wi-Fi, or when bandwidth is a concern.

- **FLAC** *(Lossless, medium bandwidth)* — Streams audio in full quality without any loss, using a moderate amount of bandwidth. Recommended over PCM for most setups — identical quality to PCM, with less network load.

- **PCM** *(Lossless, high bandwidth)* — Streams uncompressed, lossless audio but uses considerably more bandwidth than FLAC for identical quality. Only use this if your device or firmware has trouble playing FLAC files.

## Custom Sendspin Connection

If the app cannot reach your Music Assistant server with the default connection, open **Advanced configuration** and turn on **Custom Sendspin connection**. Then set the connection manually.

| Field | Description |
|---|---|
| **Host** | The hostname or IP address of your Music Assistant server. If empty, the app uses the server host. |
| **Port** | Port number (default: `8095`). If empty, the app uses the default. |
| **Path** | Sendspin endpoint path (default: `/sendspin`) |
| **ws:// or wss://** | Select **wss://** if your server uses a secure WebSocket connection (TLS) |

![Custom Sendspin Connection](screenshots/local-sendspin-player-settings/custom-sendspin-connection.jpeg)

## Car (Android Auto / CarPlay)

Once the Local Sendspin Player is enabled, the **Car Settings** section becomes available. Here you can configure how the app behaves when used in Android Auto or Apple CarPlay:

- **Tabs** — Customize which tabs are shown and in what order they appear.
- **Item actions** — Set the default action performed when tapping different item types.
- **DSP presets** — Configure what happens to the DSP for the Local Player when the phone is connected to or disconnected from the car.

For Android Auto, refer to [Android Auto](android-auto.md#car-settings).

After setting up the Local Player, you can [start using the app](home.md).
