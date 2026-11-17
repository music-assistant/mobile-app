# Party guest access: investigation

Written to ASD-STE100 rules. Date: 2026-11-10.

Sources: server `main` at `c6496f6`, tags `2.10.9` (schema 65) and `2.11.0b8` (schema 101),
and frontend `main` at `cdb4aed`. The app has no party code at this date.

## 1. What the server gives

The Party plugin (`providers/party`, domain `party`) gives guest access to one queue.
The host turns on "guest access" in the plugin settings. Then the server makes one shared
guest user (`party_guest`, role `GUEST`) and one join code for it.

The join URL has one of these forms (`helpers/guest_access.py`, `build_join_url`):

- `<external_url>/?join=<CODE>`, if the host set an external URL.
- `https://app.music-assistant.io/?remote_id=<ID>&join=<CODE>`, if remote access is on.
- `<base_url>/?join=<CODE>`, a local address. It works on the same network only.

The code has 12 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. It has no use limit and
it expires after the party duration (default 8 hours, from 1 to 168).

### 1.1 Sign-in

`auth/join_code/exchange` (`code`) needs no authentication. It returns `access_token` and
`user` (`user_id`, `username`, `role`). The server rate-limits failed attempts for each client
and for the server. Each exchange makes a new token, so the server can identify each guest device.

The guest token expires after 1 day (`TOKEN_GUEST_EXPIRATION`). The server does not renew it.
When the host turns off guest access, the server deletes the codes and the tokens. It also
closes the guest connections.

### 1.2 Guest scopes

The `GUEST` role has `library.read`, `players.read`, `players.control`, `queues.read`,
`queues.control`, `providers.read` and `config.players.read`.

The party guest user has no player filter. So the server lets a guest control every player,
not only the party queue. The web frontend limits the guest to a guest view in the UI only
(`auth.ts`, `isPartyGuest`). This is a server problem, and the app cannot correct it. Report it
to the server team before the app ships the feature.

### 1.3 Commands

| Command | Scope | Purpose |
|---|---|---|
| `party/config` | `providers.read` | Feature switches, limits, badge colors, party name, mode |
| `party/player` | `players.read` | The party queue id, or null |
| `party/url` | `providers.read` | The join URL, or null if guest access is off |
| `party/add_to_queue` (`uri`, `boost`) | `queues.control` | Add a track or a radio to the guest section |
| `party/boost_queue_item` (`queue_item_id`) | `queues.control` | Move an item to the boosted section |
| `party/skip` (`queue_item_id`, optional) | `queues.control` | Vote to skip the current item |
| `party/can_listen_in` (`web_player_id`) | `players.control` | Can this player join the party audio |
| `party/listen_in` (`web_player_id`) | `players.control` | Add this player to the party group |
| `party/stop_listen_in` (`web_player_id`) | `players.control` | Remove this player from the group |

The guest view also uses `player_queues/get`, `player_queues/items`, search and artist tracks.
The app already has these requests.

The queue items carry `extra_attributes`: `party_guest`, `party_boosted`, `party_skip_votes`
and `party_skip_voters`. The app reads the `extra_attributes` of a queue, but not of a queue item.

### 1.4 Server versions

The plugin and all 9 commands are in 2.10.9 and 2.11.0b8, with the same signatures. Server
`main` adds two items that no release has: the `queue_item_id` argument of `party/skip`, and
`skip_votes_required` in `party/config`. An older server ignores an unknown argument. Make
`skip_votes_required` optional in the model. Versions before 2.10.9 are not checked.

### 1.5 Rate limits

The server does not apply the add, boost and skip limits. The web guest view keeps a token
bucket for each limit in `localStorage` (`useRateLimiting.ts`). The app must keep the same
buckets, or a guest has no limits. A guest can avoid a client limit if the guest clears the
app data. The web has the same problem.

### 1.6 Listen-in

In "venue" mode a real player plays the party. In "remote" mode the server makes a hidden
Sendspin virtual player, and each guest device plays the audio. `party/listen_in` groups the
guest player with the host player (`cmd_set_members`). The host player must list the guest
player in `can_group_with`.

The web sends the id of its Sendspin web player. The app has a Sendspin player too, so this
can work. These items are not checked:

- The server hides the app player by default (`hide_in_ui`). It is not known if
  `can_group_with` lists a hidden player.
- The app player connects with the session of the signed-in user. It is not known how the
  server treats it when the MA session uses a guest token.

## 2. What the app has

- A QR scanner (`QrScanDialog` in `SettingsScreen.kt`). It reads only the `remote_id`
  from an `app.music-assistant.io` URL.
- Direct and WebRTC connections, and `loginWithToken` in `AuthenticationManager`.
- A precedent for a command before sign-in: `auth/exchange` (PKCE) over remote access.
- App Links and Universal Links for `music-assistant.io/app` only. The join URLs use
  `app.music-assistant.io` or the address of the server, so the OS does not open the app.
- One saved token for each server (`SettingsRepository.setTokenForServer`).

## 3. Problems to solve

1. **Token storage.** A guest token must not replace the saved token of the user.
   A user can join a party on a server that the user can also sign in to. Keep the guest
   session apart, like the web (`clearGuestSession`, `restorePersistentToken`).
2. **Session end.** A guest token ends after 1 day, or when the host turns off guest access.
   The app must then show "the party ended" and go back to the saved session. It must not
   show the login screen or try a reconnect loop. The current auto-reconnect logic expects
   a token that can sign in again.
3. **Restricted UI.** The guest must see a guest screen only, because the server lets a
   guest control every player (1.2). The main navigation, the players sheet, Android Auto
   and CarPlay must not show a guest session.
4. **Startup requests.** The app sends requests at sign-in that a guest cannot use, for
   example the ones that need `config.core.read` or `system.read`. A guest session must
   send only the requests of the guest screen.
5. **Entry.** The local join URL cannot be an App Link. A scan in the app always works.
   An App Link for `app.music-assistant.io` needs `assetlinks.json` and
   `apple-app-site-association` on that domain. The web team owns it.
6. **Rate limits.** Keep a bucket for each limit, for each party, in the settings (1.5).

## 4. Proposed phases

| Phase | Content | Risk |
|---|---|---|
| 1 | Join: scan or paste a join URL, connect, exchange the code, keep a separate guest session, handle the session end | High: connection and auth layer |
| 2 | Guest screen: now playing, queue with guest and boost badges, search, add, boost, skip vote, client buckets | Medium |
| 3 | Listen-in with the Sendspin player | High: not checked (1.6) |
| 4 | Host: show the party QR from `party/url` for a signed-in user | Low |

Phase 4 is small and it does not touch auth. It is done: see "Party" in `architecture.md`.
The web frontend already serves guests, so phases 1 to 3 are on hold.

## 5. Open questions

1. Who is the target user: a guest without the app account, a signed-in user, or both?
2. Is listen-in in scope for the first release?
3. Can the web team publish `assetlinks.json` and `apple-app-site-association` on
   `app.music-assistant.io`?
4. Does the server team accept a player filter for the party guest user (1.2)?
