# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added - 2026-08-18

#### Encrypted Sendspin connections

The built-in local player now uses the Sendspin protocol's end-to-end
encryption (Noise `KKpsk2`, `25519_ChaChaPoly_SHA256`) when the connected
Music Assistant server supports it (schema ≥ 45). Older servers keep the
legacy cleartext protocol, byte-identical to previous releases.

- Pairing is silent: on first encrypted connection the app pairs itself
  through the authenticated MA API — no PIN, no user interaction.
- New "Require encrypted connection" setting in Local Player settings: when
  enabled and the server is too old, the local player stays unavailable with
  an explanation instead of falling back to cleartext.
- **One-time player identity change**: on encrypted connections the player's
  identity is a device keypair (persisted in the app's settings storage,
  alongside the rest of the app state), so the server sees the local player
  as a new device the first time it connects encrypted. Queue
  assignments/settings bound to the old player id need to be redone once.
  Clearing the app's data resets this identity.

### Changed - 2026-01-08

#### Settings Screen Refactoring

Complete overhaul of the Settings screen with improved UX, better state management, and robust error handling.

**UI/UX Improvements:**
- Refactored to section-based layout with Material3 cards for clear visual separation
- Added distinct sections: Server Connection, Server Info, Authentication, Local Player
- Text fields in Local Player section now always visible (disabled when running, not hidden)
- Replaced checkbox with prominent toggle button: "Start Local Player" / "Stop Local Player"
- Improved visual hierarchy with primary/secondary button styling
- Added "Credentials present" indicator when token is saved while disconnected

**Authentication:**
- Fixed logout to keep websocket connection alive (no reconnection required)
- Fixed OAuth login flow (HomeAssistant, etc.) with improved reconnection handling
  - Now waits up to 10 seconds for websocket to reconnect after OAuth callback
  - Polls connection state every 250ms to ensure server info is loaded
  - Better error messages for timeout and connection issues
- Fixed error message display for incorrect credentials
  - Prevented `loadProviders()` from overriding error states
  - Added safeguards to maintain error visibility during auth failures
  - Error messages now properly display below login form

**Local Player (Sendspin):**
- Toggle button now works instantly without app restart
- MainDataSource automatically manages Sendspin lifecycle
- Configuration fields disabled (not hidden) when player is running for better UX
- Removed manual lifecycle management from ViewModel (automatic via settings observer)

**State Management:**
- Clear state-based rendering for all connection/authentication combinations:
  - State 1: Disconnected, no token → Connection form only
  - State 2: Disconnected, token present → Connection form + credentials indicator
  - State 3: Connected, not authenticated → Server info + login form
  - State 4: Connected & authenticated → Server info + user info + local player
- Proper cleanup on logout (token cleared) vs disconnect (token preserved)

**Technical Changes:**
- `SettingsViewModel`: Simplified logout/disconnect logic, removed manual Sendspin management
- `AuthenticationManager`: Enhanced OAuth callback with retry logic and timeout handling
- `AuthenticationViewModel`: Added checks to prevent provider reloading during error states
- `AuthenticationPanel`: Moved error display outside user check for consistent visibility
- Updated all section composables to use consistent SectionCard pattern

**Documentation:**
- Created comprehensive Settings screen documentation (`.claude/settings-screen.md`)
- Updated architecture documentation with references to new Settings docs
- Added Settings screen feature to README

### Fixed - 2026-01-08

- Fixed logout triggering unnecessary disconnection
- Fixed HomeAssistant OAuth login failing due to premature callback processing
- Fixed error messages not displaying when entering incorrect credentials
- Fixed brief "Connecting..." flash during logout (now expected behavior)
- Fixed Sendspin not starting/stopping on toggle without app restart

---

## Previous Changes

_No previous changelog entries. This changelog started on 2026-01-08._
