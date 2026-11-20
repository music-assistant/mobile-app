# Settings Screen

The Settings screen connects the app to a server, signs the user in, and holds the local player settings and the
app settings.

## Files

All paths are under `composeApp/src/commonMain/kotlin/io/music_assistant/client/`.

| File | Content |
|------|---------|
| `ui/compose/settings/SettingsScreen.kt` | `SettingsScreen` and the connection, server, login and Misc sections |
| `ui/compose/settings/SettingsViewModel.kt` | Connection, discovery, history, client certificate, log sharing, Misc settings |
| `ui/compose/settings/sections/Sendspin.kt` | `SendspinSettingsSection` and its subsections |
| `ui/compose/settings/SendspinSettingsViewModel.kt` | Local player settings, with a draft |
| `ui/compose/settings/sections/DiscoveredServersList.kt` | Servers found with mDNS |
| `ui/compose/settings/ClientCertificateRow.kt` | `ClientCertificateSetting` (expect) and `ClientCertificateRow` |
| `ui/compose/settings/AccentColorSection.kt` | Accent color picker |
| `ui/compose/settings/CarActionsSettings.kt` | `CarSection` |
| `ui/compose/auth/AuthenticationPanel.kt` | Provider tabs, login forms, signed-in user, logout |
| `ui/compose/auth/AuthenticationViewModel.kt` | Provider list and login actions |
| `auth/AuthenticationManager.kt` | Login, token, OAuth and logout logic (`AuthCoordinator`) |

## ViewModels

- `SettingsViewModel` gets the service client, the settings repository, the log and file sharers, the local
  network permission gate, the mDNS browser and the app version.
- `AuthenticationViewModel` gets the `AuthCoordinator` (`AuthenticationManager`) and the session state flow.
  It loads the provider list again for each new connection. It does not load it while the auth state is
  `Failed`, so the error stays on screen.
- `SendspinSettingsViewModel` gets the settings repository and a check that tells if the local player plays.
  It exposes `enabled`, `draft`, `isSavable`, `isResettable` and `isConfirmationPending`.

## Screen layout

The screen shows these parts from top to bottom:

1. `AppVersionSection`: the app version and a link to the app documentation.
2. "Exit app" button, if the user is not signed in.
3. The part that the session state selects:

| Session state | Content |
|---------------|---------|
| `Disconnected` | `AboutSection`, the local network onboarding card, `ConnectionMethodTabs`, the connection error |
| `Connecting`, `Reconnecting` | `ConnectingSection` with a Cancel button |
| `Connected` | `ServerInfoSection`, `LoginSection` |
| `Connected` and `Authenticated` | Also `SendspinSettingsSection`, and `CarSection` if the local player is on |

4. `AccentColorSection`, always.
5. `MiscSection`, always.

The back button and the back gesture go to the home screen only if the user is signed in. Otherwise back exits
the app. The light, system and dark toggle is in the top bar (`ThemeChooser`).

## Connection

`ConnectionMethodTabs` has two tabs: "Direct" and "WebRTC" (Remote Access, marked experimental). The tab is the
`preferredConnectionMethod` setting.

### Direct tab

- `DiscoveredServersList` is above the fields:
  - A Music Assistant server advertises `_mass._tcp`. The TXT record holds the server info of `get_server_info`.
  - `musicAssistantServers` (`discovery/ServerDiscovery.kt`) makes the address from the `base_url` field, with
    the scheme and the base path. If `base_url` is not there, the resolved IPv4 address and port are the fallback.
  - A tap fills the host, port, TLS and base path fields, and connects.
  - The list is empty while no server is found. A network that blocks mDNS gives no rows and no error.
  - The browse runs only while the screen collects `SettingsViewModel.discoveredServers`. It stops 5 s after
    the screen stops (`DISCOVERY_STOP_TIMEOUT_MS`).
  - The browse waits until the local network onboarding card is closed or the app has a connection history.
    On iOS, a browse shows the Local Network prompt.
- Host field. An empty host uses `Defaults.URI`.
- Base path field, for a reverse proxy that serves the server under a sub-path (for example `/ma`).
  `ConnectionInfo.normalizeBasePath` removes the extra slashes. See `ConnectionInfo` for the URL rules.
- TLS checkbox. If the port is empty or has the default of the other mode, the port changes to the default of
  the new mode: 8095 without TLS, 443 with TLS.
- Port field. If the port is 8123, a hint tells the user that 8123 is the Home Assistant port.
- `ClientCertificateSetting`, shown only when TLS is on. See "Client certificate".
- A preview of the WebSocket URL (`ConnectionInfo.previewWsUrl`).
- Connect button. It shows "Connect with saved credentials" if a token exists for a server at this address.
- History button. It opens `ConnectionHistoryDialog`. A row fills the fields of its tab. Delete removes the row
  and the token of its server.

### WebRTC tab

- Remote ID field, with a QR scanner button. The app converts the ID to uppercase and checks it with
  `RemoteId.isValid`.
- Connect button and history button, as on the Direct tab.
- See `.claude/webrtc-completion-summary.md` for the connection.

### Automatic retry

The screen retries one time after `Disconnected.Error`. It does not retry for Remote Access.

- If a saved connection exists and the fields match it, the screen connects again with the saved values.
- If no saved connection exists and the error looks like a blocked local network, the screen retries with the
  fields. The first attempt on a new install can fail because of the permission prompt.
- The flag stays set while the screen is open.

### Client certificate (mTLS)

- `ClientCertificateSetting` is an expect composable. Both platforms use `ClientCertificateRow`, which shows
  the alias with **Choose** and **Clear**.
- Android: **Choose** opens the system KeyChain chooser (`KeyChain.choosePrivateKeyAlias`). The chooser lists the
  certificates that the user installed in Android settings.
- iOS: **Choose** opens the document picker for a `.p12` file. A dialog asks for the file password. The app
  imports the identity into the Keychain. **Clear** also deletes the Keychain identity.
- If a backup brings back the alias without the Keychain item, the iOS row clears the alias.
- The app keeps one certificate (`SettingsRepository.clientCertificateAlias`) for all servers.
- See the HTTP Clients section of `.claude/architecture.md` for the TLS clients.

## Server info

`ServerInfoSection` shows the server name, the address with the base path (or "Connected via WebRTC"), the external URL
if it is different, the server version and the schema version. It shows a warning card for an unsupported
install. The Disconnect button calls `ServiceClient.disconnectByUser`.

## Sign-in

`LoginSection` holds `AuthenticationPanel`.

- While a user is signed in, the panel shows the user and a Logout button.
- Otherwise it shows one tab for each provider of type `builtin` ("Music Assistant") and `homeassistant`
  ("Home Assistant"). While the provider list is empty, it shows a Retry button.
- The panel shows an `AuthState.Error` message under the form while the error is the current state. A retry,
  a new login attempt or a reconnect replaces the state.

### Password and token

- The Music Assistant tab has a user name and a password field.
- "Use a token instead" replaces the password form with a token field. "Use a password instead" goes back.
- The user pastes a long-lived token that the web frontend makes on the profile page. The app trims the token
  and sends it with `auth`.
- The app saves the token of a server under its `server_id` (`SettingsRepository.setTokenForServer`). The next
  connection signs in with it.

### OAuth (Home Assistant)

1. The user taps "Authorize with Home Assistant".
2. The provider page opens. Android uses a Custom Tab (`CustomTabsOAuthHandler`). iOS uses an
   `ASWebAuthenticationSession` (`iosApp/OAuthWebSession.swift`).
3. The browser opens `musicassistant://auth/callback`, and the app gets the deep link.
4. `AuthenticationManager` keeps the grant until the session can use it, then it authorizes.
5. If the app does not use the grant in 20 s (`OAUTH_TOKEN_WAIT_MS = 20_000L`), the panel shows
   "Connection timeout. Please try again."

The callback has two forms:

- Without `state`: the server callback page completed the sign-in. The `code` parameter is the access token.
- With `state` (server 2.11, Remote Access): the `code` parameter is a provider code. The app sends it to
  `auth/exchange` with its PKCE verifier, and the server returns the access token.

The app uses the second form if all of these conditions are true:

- The session is a Remote Access session.
- The server schema is `SERVER_2_11_SCHEMA` or higher (`supportsAppSignIn`).
- `auth/providers` sets `supports_remote_app` for the provider.

Over Remote Access, the app hides a redirect provider if the browser cannot come back. The browser can come back
if the app uses the second form, or if the server has an External URL. `AuthenticationManager.getProviders`
removes the provider, as the web frontend does.

Only the app run that started the sign-in has the PKCE verifier. A code for an unknown `state` fails with
"This sign-in has expired. Please try again." The server accepts a code one time, so the token replaces the
code after the exchange. A retry after a reconnect sends the token, and it does not send the exchange again.

Other errors come from the server: the error of `auth/authorization_url`, of `auth/exchange`, or of the
authorization.

### Logout

1. The user taps Logout. `AuthenticationViewModel.logout` calls `AuthenticationManager.logout`.
2. The manager deletes the token of the current server and calls `ServiceClient.logout`.
3. The client sets `AuthProcessState.LoggedOut`, removes the user and sends `auth/logout`.
4. The server revokes the token and closes the socket. The client connects again without a login, and the
   panel shows the login form.

The app does not disconnect on logout. A provider load that the logout started stops with the old socket.

### Disconnect

`SettingsViewModel.disconnect` cancels a connection attempt and calls `ServiceClient.disconnectByUser`. The
token stays in the settings, so the next connection signs in with it.

## Local player

`SendspinSettingsSection` controls the built-in Sendspin player.

- The switch in the title turns the local player on or off (`sendspinEnabled`). It applies at once.
- `DeviceNameSection`: the player name. The clear button restores "My Phone".
- `CodecPreferenceSection`: Opus, FLAC or PCM.
- `HiResAudioSection`: 24-bit audio. It is off where `supportsHiResAudio` is false, and for Opus.
- "Advanced Configuration" opens:
  - `CustomConnectionSection` with `ConnectionFieldsSection`: a custom Sendspin server (`ws://` or `wss://`,
    host, port 8095 by default, path `/sendspin` by default), and "Sign in through Music Assistant".
    It applies to a direct session only.
  - `BufferSizeSection`: 5 s to 30 s in steps of 5 s, 10 s by default, with a size estimate for the codec.
- `ActionButtonsSection`: "Reset to defaults" and "Save changes".

The edits stay in the draft until Save or Reset. A change restarts a running player. So if the player plays,
Save and Reset show the "Interrupting playback" dialog first. Cancel keeps the draft.

The ViewModel only writes the settings. `LocalPlayerAdapter` owns the player and makes its config from the
settings and the endpoint. `LocalPlayerEndpoints` makes the endpoint from the MA session. A direct session needs
a token, so the player connects only after sign-in. See the Sendspin section of `.claude/architecture.md`.

## Car

`CarSection` shows only while the user is signed in and the local player is on. It has the car tabs, the car
item actions and, with `canConfigurePlayers`, the car DSP actions.

## Accent color

- One swatch for each `AccentColor` that the platform can give, in the primary color of that accent in the
  current theme. The line under the swatches names the current choice.
- `ThemeViewModel.accentColor` and `setAccentColor` read and write the setting (key `accent_color`).
- See the Accent color item of `.claude/architecture.md`.

## Misc

- "Allow landscape on all devices".
- "Volume buttons control the selected player", only where `supportsVolumeKeyRouting` is true (Android). See
  `.claude/volume-control.md`.
- "Share logs". If a crash log exists, "Share crash logs" and a delete button.
