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

### OAuth Authentication (e.g., HomeAssistant)
1. User clicks "Authorize with [Provider]" button
2. OAuth browser window opens (app goes to background)
3. User completes OAuth flow in browser
4. Deep link callback returns to app
5. AuthenticationManager waits up to 10 seconds for websocket reconnection
6. Once connected, authorization completes
7. UI updates to State 4

**OAuth Reconnection Logic:**
- Polls connection state every 250ms
- Checks for both `SessionState.Connected` AND `serverInfo != null`
- Times out after 10 seconds with error message

## Logout Behavior

### Logout Process
1. User clicks Logout button in UserInfoSection
2. `SettingsViewModel.logout()` calls `ServiceClient.logout()`
3. Server receives logout request
4. Token is cleared locally
5. Session state updates to `AuthProcessState.LoggedOut`
6. **Websocket connection remains active** (no disconnection)
7. UI updates to State 3 (shows login form again)

**Important:**
- Logout does NOT disconnect from server
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
4. `AuthenticationPanel` uses `LaunchedEffect` to capture error in local state
5. Error message displayed at bottom of panel (outside user check, so always visible)

**Key Implementation Details:**
- `AuthenticationViewModel.loadProviders()` checks if providers already loaded to avoid overriding error states
- Session state collector skips `loadProviders()` when `AuthProcessState.Failed`
- Error messages persist until next successful auth or state change

### OAuth Errors
- Connection timeout: "Connection timeout. Please try again."
- Authorization failure: Error message from server
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
