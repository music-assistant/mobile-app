# Sendspin encryption — manual integration test

Validates the encrypted Sendspin path against a real Music Assistant server.
Device-dependent: run manually (or in a nightly lane), not in PR CI. The
common test suite covers the protocol logic; this run validates real-server
interop, the silent-pairing RPC, and WebRTC frame behavior.

## Prerequisites

- **Dev server**: MA dev build with aiosendspin ≥ 9.1.0 and schema ≥ 45
  (encrypted Sendspin + `sendspin/pair_web_player`). `scripts/run-local-ma.sh`
  can host it.
- **Stable server**: any current-stable MA build with schema < 45, for the
  legacy-regression half.
- A debug build of this app on a device/simulator, logged in to the server
  under test. Watch logs with tags `EncryptedSession`, `SendspinHandshake`,
  `SilentPairingCoordinator`, `PairingHandler`, `LocalPlayerController`.

## 1. Fresh install → sentinel → silent pair → long-term reconnect (proxy mode)

1. Fresh-install the app (or clear app storage) so no Sendspin identity exists.
2. Log in to the **dev server** (schema ≥ 45), enable the local player
   (default proxy connection).
3. Expect in order:
   - the first Sendspin protocol frame (after the proxy `auth`/`auth_ok`
     pre-exchange) is `client/init` — server log shows an encrypted client
     connecting;
   - hello exchange completes on the **sentinel** PSK (`trust_level: none`);
   - `SilentPairingCoordinator` fires exactly one `sendspin/pair_web_player`;
   - the server re-handshakes to the pairing PSK, the pairing activation
     arrives, `client/pair-finalize` → `server/pair-finalize`, record
     persisted;
   - the server re-handshakes to the new long-term PSK; hello now asserts
     `trust_level: user`.
4. Play audio; verify synchronized playback (encrypted audio frames).
5. Kill the app, reopen: the reconnect must match the **long-term record**
   directly (no sentinel, no pairing RPC) and assert `trust_level: user`.

## 2. Direct (custom connection) mode

1. In Local Player settings enable "Custom Sendspin connection" pointing at
   the dev server's dedicated Sendspin port; start the player.
2. Same expectations as scenario 1, but without the proxy `auth`/`auth_ok`
   pre-exchange. Pointing the custom connection at the MA main port must
   still run the pre-exchange before `client/init`.

## 3. Legacy clients refused (`allow_legacy_clients=false`)

1. On the dev server set the Sendspin provider's `allow_legacy_clients` to
   false.
2. The (already-paired) app must keep working — it never sends a legacy hello
   on a schema ≥ 45 server.

## 4. Legacy regression against current-stable MA

1. Log in to the **stable server** (schema < 45); enable the local player.
2. First frame must be the legacy `auth` (proxy) / `client/hello` (custom);
   playback works exactly as before this feature.
3. Enable "Require encrypted connection": the player must become unavailable
   with the explanatory message, and no Sendspin connection may be attempted
   (verify no `client/init`/`client/hello` in server logs).
4. Disable the toggle: legacy playback resumes.

## 5. WebRTC (remote access) mode

1. Connect to the dev server via remote access (WebRTC).
2. Scenario 1 expectations over the data channel, plus:
   - large encrypted messages fragment/reassemble without audio corruption;
   - killing the sendspin data channel server-side surfaces channel
     exhaustion and the app negotiates a fresh channel automatically
     (one `forceWebRTCReconnect`, no retry storm).

## 6. Identity persistence

1. Note the player identity (client_id) on the dev server.
2. Restart the app: identity unchanged, no new pairing.
3. Clear the app's data (or uninstall without a backup restore): a NEW
   identity appears (storage loss is accepted as identity loss) and silent
   pairing runs again cleanly.
