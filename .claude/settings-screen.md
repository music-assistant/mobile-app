# Settings Screen Documentation

## Overview

The Settings screen provides configuration for server connection, authentication, and local player (Sendspin) management. The screen has been refactored to use a clean, section-based UI with clear state-based rendering.

## Location

- **Screen**: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/settings/SettingsScreen.kt`
- **ViewModel**: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/settings/SettingsViewModel.kt`
- **Auth Panel**: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/auth/AuthenticationPanel.kt`
- **Auth Manager**: `composeApp/src/commonMain/kotlin/io/music_assistant/client/auth/AuthenticationManager.kt`

## UI States

The Settings screen adapts its UI based on connection and authentication state:

### State 1: Disconnected, No Token
**Displays:**
- Server connection fields (host, port, TLS checkbox)
- Connect button

### State 2: Disconnected, Token Present
**Displays:**
- Server connection fields (host, port, TLS checkbox)
- "Credentials present" indicator
- Connect button

### State 3: Connected, Not Authenticated
**Displays:**
- Server info section with disconnect button
- Login section with authentication providers (tabs)

### State 4: Connected and Authenticated
**Displays:**
- Server info section with disconnect button
- User info section with logout button
- Local player section (Sendspin configuration)

## Section Components

### ServerConnectionSection
- Text fields for host, port, and TLS checkbox
- Client certificate row (mTLS), shown when TLS is on. `ClientCertificateSetting` is an expect composable:
  - Android: **Choose** opens the system KeyChain chooser. The chooser lists the certificates that the user installed in Android settings.
  - iOS: **Choose** opens the document picker for a `.p12` file. A dialog asks for the file password. The app imports the identity into the Keychain.
  - **Clear** removes the selection. On iOS, it also deletes the Keychain identity.
  - The app keeps one certificate for all servers. It sends the certificate only to a server that asks for one.
  - OAuth login on iOS uses `ASWebAuthenticationSession`, which cannot use the imported identity. For OAuth behind mTLS, install the certificate as an iOS profile too.
- Shows "Credentials present" badge when token exists
- Connect button

### ServerInfoSection
- Displays current connection (host:port)
- Shows server version and schema version
- Disconnect button (OutlinedButton)

### UserInfoSection
- Displays logged-in user information
- Logout button (OutlinedButton)

### LoginSection
- Wraps AuthenticationPanel in a SectionCard
- Shows authentication provider tabs (builtin, OAuth providers)
- The Music Assistant form has a "Use a token instead" link under the password field
- Provider-specific login forms
- Error messages displayed below login form

### SendspinSection (Local Player)
**Layout:**
- Text fields on top: Player name, Port, Path
- Fields are **disabled** (not hidden) when player is running
- Toggle button on bottom: "Start Local Player" / "Stop Local Player"

**Behavior:**
- Toggle instantly starts/stops Sendspin (no app restart needed)
- MainDataSource automatically manages Sendspin lifecycle based on `sendspinEnabled` setting
- Configuration changes only allowed when player is stopped

## Authentication Flow

### Builtin Authentication
1. User enters username and password
2. Clicks Login button
3. On success: User is authenticated and UI updates to State 4
4. On failure: Error message displayed below login form

### Token Authentication
1. The user taps "Use a token instead" under the password field of the Music Assistant form. "Use a password instead" goes back.
2. The user pastes a long-lived token. The web frontend makes the token on the profile page.
3. The user clicks the Login button. The app trims the token and sends the `auth` command.
4. On success, the app saves the token for the server. The next connection signs in with it.
5. On failure, the server error shows below the form, for example "Invalid or expired token".

### OAuth Authentication (e.g., HomeAssistant)
1. The user clicks the "Authorize with [Provider]" button.
2. The provider page opens. Android uses a Custom Tab. iOS uses an `ASWebAuthenticationSession`.
3. The user signs in on the provider page.
4. The browser opens `musicassistant://auth/callback`, and the app gets the deep link.
5. `AuthenticationManager` keeps the result until the session is in `AwaitingAuth`, then it authorizes.
6. The UI changes to State 4.

The callback has two forms:
- Without `state`: the server callback page completed the sign-in. The `code` parameter is the access token.
- With `state` (server 2.11, Remote Access): the browser returned through the `app.music-assistant.io`
  callback page. The `code` parameter is a provider code. The app sends it to `auth/exchange` with its
  PKCE verifier, and the server returns the access token.

The app uses the second form if all of these conditions are true:
- The session is a WebRTC (Remote Access) session.
- The server schema is `SERVER_2_11_SCHEMA` or higher (`supportsAppSignIn`).
- `auth/providers` sets `supports_remote_app` for the provider.

Otherwise the app uses the first form, as on server 2.10. The second form does not need an External URL
on the server, because the browser does not open the server. The browser opens Home Assistant only.

Over Remote Access, the app hides a redirect provider if the browser cannot come back. The browser can
come back if the app uses the second form, or if the server has an External URL. Without one of these,
the server callback is an internal address. `AuthenticationManager.getProviders` removes the provider,
as the web frontend does.

Only the app run that started the sign-in has the verifier. A code for an unknown `state` fails.
The server accepts a code one time, so the token replaces the code after the exchange.
A retry after a reconnect sends the token again, and does not send the exchange again.
If the app does not use the result in 20 seconds, it shows "Connection timeout. Please try again."

## Logout Behavior

### Logout Process
1. User clicks Logout button in UserInfoSection
2. `SettingsViewModel.logout()` calls `ServiceClient.logout()`
3. Server receives logout request
4. Token is cleared locally
5. Session state updates to `AuthProcessState.LoggedOut`
6. The server revokes the token and closes the WebSocket of that token. The client reconnects without a login.
7. UI updates to State 3 (shows login form again)

**Important:**
- The app does not disconnect on logout. The server closes the socket, and the client reconnects on its own.
- `AuthenticationViewModel` loads the provider list again on each new connection. A load that the logout started dies with the old socket, and the manager reports no error for it.
- Sendspin lifecycle managed by MainDataSource based on auth state
- User can immediately log back in without reconnecting

## Disconnect Behavior

### Disconnect Process
1. User clicks Disconnect button in ServerInfoSection
2. `SettingsViewModel.disconnect()` calls `ServiceClient.disconnectByUser()`
3. Websocket closes
4. **Token is preserved** in settings
5. Sendspin stops (MainDataSource handles cleanup)
6. UI updates to State 1 or 2 (depending on token presence)

**Important:**
- Disconnect preserves token for next connection
- When reconnecting, saved token is used for auto-login

## Error Handling

### Login Errors
**Issue:** Authentication failures must be displayed to user

**Solution:**
1. `ServiceClient.login()` sets `AuthProcessState.Failed` with error message
2. `AuthenticationManager` monitors session state and sets `authState = Error(message)`
3. `AuthenticationViewModel` prevents `loadProviders()` when in Failed state
4. `AuthenticationPanel` shows the message while `AuthState.Error` is the current state. A retry of the provider load, a new login attempt or a reconnect replaces the state and removes the message.
5. Error message displayed at bottom of panel (outside user check, so always visible)

**Key Implementation Details:**
- `AuthenticationViewModel.loadProviders()` checks if providers already loaded to avoid overriding error states
- Session state collector skips `loadProviders()` when `AuthProcessState.Failed`
- Error messages persist until next successful auth or state change

### OAuth Errors
- Server refuses to start the sign-in: Error message from `auth/authorization_url`
- Connection timeout: "Connection timeout. Please try again."
- Authorization failure: Error message from server
- Exchange failure: Error message from `auth/exchange`
- Code for an unknown sign-in: "This sign-in has expired. Please try again."
- No connection: "Connection lost. Please try again."

## Sendspin Integration

### Lifecycle Management
- **MainDataSource** manages Sendspin client lifecycle (singleton pattern)
- Watches `settings.sendspinEnabled` flow for changes
- Automatically starts/stops based on:
  - `sendspinEnabled` setting toggle
  - Connection state (stops on disconnect)
  - Authentication state (requires authenticated connection)

### Settings ViewModel Role
- Exposes settings flows: `sendspinEnabled`, `sendspinDeviceName`, `sendspinPort`, `sendspinPath`
- Provides setter methods that update SettingsRepository
- **Does NOT** manually manage Sendspin lifecycle
- MainDataSource reacts to setting changes automatically

### Configuration Requirements
- Player name: User-friendly identifier for the device
- Port: Default 8927 (Sendspin protocol port)
- Path: Default "/sendspin" (WebSocket endpoint path)
- All settings stored in SettingsRepository and persisted

## Auto-Reconnection

### Connection Error Recovery
The Settings screen includes auto-reconnect logic for improved reliability:

```kotlin
LaunchedEffect(sessionState) {
    if (sessionState is SessionState.Disconnected.Error &&
        savedConnectionInfo != null &&
        !autoReconnectAttempted
    ) {
        autoReconnectAttempted = true
        viewModel.attemptConnection(host, port, isTls)
    }
}
```

**Behavior:**
- Attempts one automatic reconnection on connection error
- Uses saved connection info (host, port, TLS)
- Flag prevents infinite retry loops
- Resets on successful connection

## UI/UX Improvements

### Section-Based Design
- Consistent `SectionCard` component with rounded corners and subtle background
- `SectionTitle` for clear section labeling
- Proper spacing between sections (16.dp)
- Material3 design system throughout

### Form Field States
- Disabled fields show visual feedback (60% opacity)
- Always visible fields (not hidden) for better UX
- Clear button states (enabled/disabled) based on input validation

### Button Hierarchy
- Primary actions: Filled `Button` (Connect, Start Local Player, Login)
- Secondary actions: `OutlinedButton` (Disconnect, Logout, Stop Local Player)
- Consistent full-width buttons within sections

### Navigation
- Back button only visible when authenticated
- `BackHandler` prevents accidental exit when not authenticated
- Clear visual hierarchy with action buttons

## Dependencies

### ViewModel Dependencies
```kotlin
class SettingsViewModel(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository
) : ViewModel()
```

### Panel Dependencies
```kotlin
class AuthenticationViewModel(
    private val authManager: AuthenticationManager,
    private val settings: SettingsRepository,
    private val serviceClient: ServiceClient
) : ViewModel()
```

### Key StateFlows
- `SettingsRepository.connectionInfo`: Server connection details
- `SettingsRepository.token`: Authentication token
- `SettingsRepository.sendspinEnabled/DeviceName/Port/Path`: Sendspin configuration
- `ServiceClient.sessionState`: Connection and auth state
- `AuthenticationManager.authState`: UI-specific auth state

## Testing Scenarios

### Scenario 1: First-time Setup
1. Launch app → Shows State 1
2. Enter server details → Connect
3. Choose provider → Login
4. Verify State 4 shown with all sections

### Scenario 2: OAuth Login
1. Connect to server
2. Select HomeAssistant provider
3. Complete OAuth in browser
4. Return to app → Wait for connection
5. Verify authentication completes
6. Check error handling if connection fails

### Scenario 3: Logout/Login Cycle
1. Start authenticated (State 4)
2. Click Logout → Verify stays connected (State 3)
3. Log back in → Verify smooth transition to State 4
4. Verify Sendspin state managed correctly

### Scenario 4: Disconnect/Reconnect
1. Start authenticated (State 4)
2. Click Disconnect → Verify token saved
3. Reconnect → Verify auto-login with saved token
4. Check "Credentials present" shown in State 2

### Scenario 5: Local Player Toggle
1. Authenticate to State 4
2. Configure player settings
3. Click "Start Local Player"
4. Verify fields disabled
5. Verify player appears in MA server
6. Toggle off → Verify fields re-enabled

### Scenario 6: Error Handling
1. Enter wrong password
2. Verify error message displays
3. Correct credentials → Verify error clears
4. Test OAuth timeout scenario
5. Verify all error messages display correctly

## Common Issues and Solutions

### Issue: Error messages not showing
**Cause:** `loadProviders()` overriding error state
**Solution:** Check `AuthProcessState.Failed` before loading providers, prevent redundant loads

### Issue: Sendspin doesn't start on toggle
**Cause:** Not authenticated or connection lost
**Solution:** Verify State 4 (authenticated), check MainDataSource logs

### Issue: OAuth returns to app but doesn't authenticate
**Cause:** Websocket not reconnected yet
**Solution:** AuthenticationManager waits up to 10 seconds for connection

### Issue: Brief "Connecting..." flash on logout
**Cause:** UI recomposition during state transition
**Solution:** Expected behavior, harmless artifact

## Future Enhancements

- [ ] Add connection test button (ping server)
- [ ] Show Sendspin connection status (connecting/connected/error)
- [ ] Add biometric authentication option
- [ ] Server discovery (mDNS/Zeroconf)
- [ ] Multiple server profiles
- [ ] Export/import settings
- [ ] Advanced Sendspin settings (buffer size, etc.)
