# Sendspin Encryption Support — Implementation Plan

Status: IMPLEMENTED (2026-08-18)

> Post-landing amendment (operator decision): the identity/trust store is
> persisted in the app's regular settings storage (SharedPreferences /
> NSUserDefaults) rather than Keystore/Keychain — the MA auth token already
> lives there, and a split secrecy tier protected the least valuable secrets
> while adding platform-keystore failure modes. The `SecureKeyStore`
> expect/actual described below became the common `SendspinKeyStore`.
Branch: `feat/sendspin-encryption`
Spec: `~/Development/sendspin/spec` (connection.md, messaging.md, pairing.md, management.md)
Server reference: `../server` (aiosendspin 9.1.0, provider `music_assistant/providers/sendspin/`)

## Goal

Add Sendspin Noise-encrypted connections (Noise `KKpsk2`, `25519_ChaChaPoly_SHA256`) to the mobile app's built-in Sendspin player with silent pairing and a minimal management role, while keeping the legacy unencrypted protocol byte-identical for older MA servers.

## Implementation Summary

Add Sendspin Noise-encrypted connections (spec: Noise `KKpsk2`,
`25519_ChaChaPoly_SHA256` suite only) to the mobile app's built-in Sendspin
player, while keeping the legacy unencrypted protocol byte-identical for older
MA servers (2 major versions of compat required). Encryption is implemented as
a new **protocol session** layer (legacy and encrypted implementations behind a
common interface), a hand-rolled vector-tested Noise KKpsk2 core over
`cryptography-kotlin` primitives, a persisted X25519 identity + PSK trust store
(app settings storage; see the post-landing amendment), silent Pairing-PSK pairing driven through the
authenticated MA API (`sendspin/pair_web_player`), and a minimal management
role. Mode selection is a pure version gate on the authenticated MA session.

### Decisions locked with operator

1. **Mode selection: version gate only.** Encrypted iff the authenticated MA
   server reports `schema_version >= 45` (see "Version gate" below). No
   probe-and-downgrade. Custom Sendspin connection settings are always used in
   conjunction with an MA server, so the same gate applies to them.
2. **Accept one-time player-identity change.** Encrypted `client_id` *is* the
   X25519 public key; the legacy UUID identity is not migrated.
3. **Scope:** baseline encryption + Pairing-PSK-only pairing (silent, via MA
   API) + minimal management role + "require encryption" setting.
   **Out of scope:** dynamic/static PIN pairing UX, AES-GCM suite, source role,
   server-initiated (mDNS-advertised) connections.

### Version gate (resolved)

- Server-side history: Sendspin encryption landed at schema 38
  (`385390bb9`, 2026-07-19); the current pairing semantics
  (`a9e50903c`, schema 44) and automatic built-in web-player pairing
  (`785bbd8b1`, **schema 45**, 2026-08-12) complete the server behavior this
  plan depends on.
- **Gate: `ServerInfo.schemaVersion >= 45`** (already available on the
  authenticated session: `data/model/server/ServerInfo.kt`,
  `KtorServiceClient` session state). Below 45, or when `schemaVersion` is
  null/unknown → legacy mode. At/above 45 → encrypted mode, no fallback.
- "Require encryption" toggle: when on and the gate resolves to legacy, the
  local player is surfaced as unavailable with an explanatory message; no
  legacy connection is made.

## Background (investigated facts)

- **Wire flow (encrypted):** `client/init` (cleartext text frame; `client_id`,
  `version: 1`, `suite`) → `server/init` (`server_id`, `version`) →
  `noise/handshake` msg 1 (server→client; inner payload `{"psk_id": ...}`) →
  `noise/handshake` msg 2 (client→server; inner payload literal `{}`) → both
  sides switch to Noise transport mode: **all** subsequent messages are WS
  binary frames containing Noise ciphertexts; after AEAD-decrypt, byte 0 is the
  message type (0 = JSON UTF-8; 4-7 player-role audio; 2/3 fragmentation).
  Then `server/hello` → `client/hello` → `server/activate`, all encrypted.
- **Noise details:** pattern `KKpsk2`; the **server is the Noise initiator**
  regardless of who opened the WS. Prologue = exact raw bytes of `client/init`
  followed by `server/init` as transmitted (never re-encoded). PSK mixed at the
  psk2 position. Max Noise message 65535 bytes → app payload ≤ 65518; larger
  messages use fragment types 2/3 (one in flight per direction). Re-handshake:
  `noise/handshake` re-run inside the encrypted channel (as type-0 JSON
  messages), prologue = prior handshake hash `h`; msg 2 still sent under old
  keys; after completion `server/hello` → `client/hello` → `server/activate`
  repeat. **No other messages may flow during the re-handshake exchange.**
- **Hello payload shapes differ by mode** (messaging.md §server/hello,
  §client/hello): encrypted `server/hello` carries only `name`; encrypted
  `client/hello` carries `name`, `device_info?`, `trust_level`,
  `supported_roles` (+ support objects), `supported_pair_methods`,
  `unpaired_access` — and **omits** `client_id` and core `version` (they came
  from `client/init`). Legacy hellos keep today's shapes exactly.
- **PSK categories:** Sentinel (published constant
  `SHA-256("sendspin-sentinel-psk-v1")`), per-device Pairing PSK, per-server
  long-term PSK. `psk_id = base64url(SHA-256("sendspin-psk-id-v1" || PSK))`;
  one namespace across categories (collisions rejected at config time). Client
  selects the PSK by the `psk_id` in noise msg 1; lookup miss ⇒ close socket.
  We use the **stored-pubkey model**: long-term PSKs persisted with the
  server's `server_id`, verified against `server/init` post-match.
- **Handshake failures** (any phase): close the WS with no application-level
  error. 30 s per-message timeout during init/handshake phases.
- **Activation rules** (messaging.md §server/activate): no client/time,
  client/state or any other message before the first `server/activate`.
  Admission table per matched-PSK category; violations answered with
  `client/goodbye` `pairing_required`/`unauthorized` or `pair/abort
  method_not_supported` per the spec's three ordered rules. `active_roles`
  persists across activations that omit it; playback-capability re-evaluated
  each activation.
- **`server/unpair`** (messaging.md) — three branches, all explicit:
  1. `user`-trust via **stored-pubkey** record: delete the record, send
     `client/goodbye {reason:'unpaired'}`, close.
  2. `user`-trust via **shared-PSK** record: **retain** the record (it may
     authenticate other servers), still send `client/goodbye
     {reason:'unpaired'}` and close.
  3. `trust_level='none'` session: ignore.
- **Server side (MA dev):** aiosendspin dispatches on the client's first text
  frame — `client/init` ⇒ encrypted, `client/hello` ⇒ legacy (gated by
  `allow_legacy_clients`). The main-port Sendspin proxy (`{"type":"auth"}` /
  `auth_ok` pre-exchange) is a transparent pipe afterwards, so the encrypted
  protocol runs through proxy mode unchanged.
- **Silent pairing:** the app advertises `model="Mobile Application"`,
  `manufacturer="Music Assistant"` ⇒ the server classifies it as a web player.
  MA API command **`sendspin/pair_web_player`** (scope PLAYERS_CONTROL) takes
  the client's pairing token, awaits the client's Sendspin connection,
  re-handshakes to the Pairing PSK, runs the Pairing-PSK flow, and binds the
  record to the calling MA user. Idempotent when a valid record exists.
- **Pairing token:** `"SP:0" + base32(client_pubkey(32) || pairing_psk(32))`
  with `=` stripped and `2→9` transliterated; 107 chars. Reference vector in
  pairing.md §Pairing Token.
- **Pairing-PSK flow (client obligations):** keep the Pairing PSK among PSK
  candidates whenever enabled (re-handshake to it must succeed); on
  `server/activate {activities:['pairing'], pairing:{method:'pairing_psk'}}`
  verify the session's matched PSK **is** the Pairing PSK (else `pair/abort
  method_not_supported`); generate a fresh 32-byte long-term PSK; send
  `client/pair-finalize {long_term_psk}` (base64url); await `server/pair-finalize {}`
  then persist the record; expect a server-initiated re-handshake to the new
  PSK. Attempt timeout ~2 min ⇒ `pair/abort attempt_timeout`. A cancelling
  `server/activate` discards the attempt silently (persist nothing).
- **Management (spec-required):** `management/list-records`, `add-record`,
  `remove-record`, `get-pairing-config`, `set-pairing-config`,
  `open-pairing-window`, each answered by one `management/result`
  (`ok|permission_denied|already_exists|invalid|not_found|storage_exhausted`,
  optional `data`, optional `storage`). Valid only with `'management'` in
  activities on a long-term-PSK session; otherwise `permission_denied`. At most
  one in flight. Constraints: `record_mode.psk_id` must reference a shared-PSK
  record; a referenced shared record cannot be removed; psk_id collisions
  across categories rejected `already_exists`; config changes apply to the
  live PSK-candidate set immediately; removing the requester's own record ⇒
  respond, then `client/goodbye unauthorized`.
- **Crypto (researched):** no production KMP Noise library exists; hand-roll
  the KKpsk2 state machine (~300 lines of pure protocol logic) over primitives.
  Primitives: `dev.whyoleg.cryptography:cryptography-core:0.6.0` +
  `cryptography-provider-jdk` (Android) / `cryptography-provider-apple`
  (iOS, CryptoKit) — X25519, ChaCha20-Poly1305, SHA-256, HMAC, HKDF confirmed
  per release notes on both targets, single commonMain API. Pre-1.0: pin the
  version, isolate behind our own primitive interface. Validate against
  reference vectors (mcginty/snow psk2 family; generate
  `Noise_KKpsk2_25519_ChaChaPoly_SHA256` vectors with cacophony if absent).

## Implementation Plan

### Key structural decision: protocol sessions, not scattered mode flags

Review finding: the current `MessageDispatcher` owns legacy auth, server-hello
side effects (immediate initial `client/state` + clock start), and routing,
while `SendspinClient.runStateMachine` reacts to every raw
`WebSocketState.Connected` by sending auth/hello. Threading encrypted-mode
conditionals through that would be brittle and violates the encrypted
sequencing rules. Instead, the connection-establishment concern is **replaced**
by a session abstraction; the audio pipeline, roles, and app-level state
machine stay.

```
SendspinClient
  └── SendspinProtocolSession (NEW interface)
        ├── LegacySession     — extracted current behavior, byte-identical wire
        └── EncryptedSession  — init + Noise handshake + framing + re-handshake
              └── NoiseChannel (KKpsk2 core + framing)
  Session consumes: SendspinTransport (unchanged interface)
  Session produces: ordered inbound application messages (single pump),
                    demuxed audio chunks (see "Audio demux" below),
                    lifecycle events (ProtocolReady(serverId,
                    matchedPskCategory, trustLevel, isReconnectEpoch) —
                    hello exchange complete, pre-activate;
                    Activated(activities, activeRoles, isReconnectEpoch);
                    Reconnecting(attempt); Disconnected;
                    RehandshakeCompleted; Failed(cause)),
                    a serialized outbound sender (suspending, quiesced
                    during re-handshake)
```

Lifecycle contract (fixes the review blocker):

- **Single source-ordered inbound stream (review round 2):** the current
  interface's two independent hot flows (`textMessages`/`binaryMessages`)
  cannot preserve source frame order across a text→binary transition — unsafe
  exactly at handshake completion (encrypted `server/hello` racing the text
  collector) and on reconnect. The transport interface is refactored to emit
  **one** `Flow<InboundTransportEvent>` — **every event carries the epoch**:
  `Connected(epoch, isReconnect)`, `Text(epoch, string)`,
  `Binary(epoch, bytes)`, `Disconnected(epoch)`, `Error(epoch, cause)` —
  produced from the single WS `incoming` loop (resp. single DataChannel
  callback path), with an epoch's `Connected` event published **before** any
  frame of that epoch. The session tracks the current epoch and **drops any
  event whose epoch is not current** (a stale listener can still emit after a
  new epoch's `Connected` because listener jobs are cancelled
  asynchronously). Startup ordering is fixed: construct the session, start
  its event collector (subscribe), **then** call `transport.connect()`; only
  the session initiates the first wire frame, so a `Connected` emitted
  synchronously during `connect()` cannot be lost. **Delivery is lossless:**
  inbound events ride a suspending/backpressured channel, never the
  bounded-`tryEmit`-with-drop pattern today's `DataChannelWrapper` shared
  flows use — a dropped control frame would strand the Noise handshake or
  activation state machine. Audio and control share the one ordered path;
  any buffering policy must preserve source order and must never drop
  protocol frames. The session is the only
  collector and demultiplexes to the dispatcher only after ordering is
  established. `MessageDispatcher` consumes the session's ordered pump, never
  the transport directly. (Legacy path is adapted to the same event stream.)
- **Audio demux (session-owned in both modes):** `SendspinClient` no longer
  collects `transport.binaryMessages` directly. The session demultiplexes
  audio from its single ordered pump and delivers it on a dedicated audio
  output consumed by `SendspinClient` → `AudioPipeline.processBinaryMessage`:
  in legacy mode every raw binary transport frame is an audio chunk (passed
  through byte-identical); in encrypted mode audio is the decrypted type-4..7
  application payload delivered **complete — including the leading type byte
  and timestamp header, byte-compatible with `BinaryMessage.decode`**
  (fragmentation reassembled by `NoiseFraming` restores the original payload
  bytes exactly before delivery). The legacy golden tests must cover the
  pass-through, and `EncryptedSessionTest` includes an encrypted-audio case
  (plain and fragmented type-4) asserting the exact bytes handed to
  `AudioPipeline.processBinaryMessage`.
- The session performs the proxy `auth`/`auth_ok` pre-exchange itself **when
  `config.requiresAuth` is set** — this moves `sendAuth` out of
  `SendspinClient`'s Connected reaction entirely; `LegacySession` then sends
  `client/hello` as today. `requiresAuth` semantics are **unchanged**: it is
  computed exactly as today (`mainConnectionPort != null && serverPort ==
  mainConnectionPort` in `SendspinConfig`), so a custom connection pointed at
  the MA main port still runs the proxy pre-exchange, and WebRTC (whose
  config has `mainConnectionPort=null`; auth inherited from the ma-api
  channel) never does. The encrypted session runs the same pre-exchange
  before `client/init` when `requiresAuth` is set (the proxy is a transparent
  pipe after `auth_ok`). Handshake tests assert the first frames for proxy,
  custom-same-port, custom-distinct-port, and WebRTC configurations in both
  modes.
- `SendspinClient` no longer sends anything on raw transport `Connected` and
  no longer collects the transport at all; it reacts to session lifecycle
  events only (the session forwards connection lifecycle as
  `Reconnecting(attempt)` / `Disconnected` events derived from the transport
  stream it exclusively collects). `SendspinState.Ready` is emitted on the
  session's `Activated` event in encrypted mode (first admissible
  `server/activate`); in legacy mode `ProtocolReady` (server/hello, as today)
  maps to `SendspinState.Ready` — legacy has no activate.
- **Reconnect auto-resume moves with the Connected reaction.** Today
  `SendspinClient`'s raw-Connected handler also performs auto-resume
  (`wasStreaming` + reconnect attempt ≤ `RECONNECT_AUTO_RESUME_MAX_ATTEMPTS`
  → `mediaPlayerController.resume()`). This logic is preserved but re-homed:
  the attempt count and `wasStreaming` are tracked by `SendspinClient` from
  the session's `Reconnecting(attempt)` lifecycle event (which drives
  `SendspinState.Reconnecting` exactly as the raw transport state does
  today), and the resume call now fires on the session's post-reconnect
  readiness event — for `LegacySession` that is `ProtocolReady` with
  `isReconnectEpoch=true`; for
  `EncryptedSession` it is `Activated` with `isReconnectEpoch=true`, i.e.
  only after the first admissible `server/activate` of the new epoch (resume
  triggers server-side playback and must not race activation gating). The
  attempt-≤9 ladder and skip-log behavior are unchanged.
- Transport `connect()` failure surfaces as `Failed` (the current
  `SendspinWsHandler.connect()` swallows exceptions into an Error state —
  session observes the state, not the call).
- Reconnects: `SendspinWsHandler`'s auto-reconnect emits
  `Connected(epoch, isReconnect=true)` on the event stream *before* the new
  epoch's first frame (note: today the handler flips state and starts the
  listener before invoking `onReconnected` — that ordering is part of the
  refactor). `EncryptedSession` re-runs init + full handshake (fresh
  ephemerals, fresh prologue) on every reconnect epoch before re-emitting
  readiness; frames from a stale epoch are discarded.
  **WebRTC data channels are terminal:** `WebRTCDataChannelTransport`
  disconnect/close are no-ops by design and channels are single-use — a
  session failure over WebRTC ends the session; the factory negotiates a new
  channel. The factory must observe the session's definitive
  `Ready`/`Failed` outcome (awaitable) instead of assuming
  `connectWithTransport()` returning means the channel was consumed usably.
  A terminal `Failed` on an attached WebRTC channel is surfaced as **channel
  exhaustion** so the existing controller path
  (`WebRTCSendspinChannelExhausted → forceWebRTCReconnect()`) negotiates a
  fresh wrapper, rather than leaving a dead handler marked unused and retried.

### Encrypted sequencing specifics (in `EncryptedSession` + dispatcher changes)

- `handleServerHello` side effects move: in encrypted mode the initial
  `client/state`, clock-sync start, and `StateReporter.start()` are deferred
  until the first admissible `server/activate`. The session enforces
  "send nothing before activate".
- Admission table enforcement per matched PSK category, with the spec's three
  ordered rejection rules (`pairing_required` / `unauthorized` goodbye,
  `pair/abort method_not_supported`). `active_roles` persistence across
  activations implemented in the session.
- **Outbound serialization:** all sends go through one suspending queue owned
  by the session, exposed as the session's single outbound sender interface.
  `MessageDispatcher` and `StateReporter` are re-pointed from
  `SendspinTransport` to this sender — every existing send call site routes
  through it (`sendHello`, `sendTime`, `sendState`, `sendGoodbye`,
  `sendCommand`; `sendAuth` moves into the session itself) and nothing else
  holds a transport reference. The queue fails pending sends fast on session
  `Failed`/close; `sendGoodbye` during `stop()` is a normal queued send. On
  inbound re-handshake msg 1, the queue is paused, msg 2 is
  sent under the old keys, keys are swapped, and the queue resumes after the
  post-re-handshake `server/activate` (hello exchange runs through the same
  gate). Audio/state/time senders never interleave with the exchange.
- `server/unpair` handling: all three branches per Background (stored-pubkey
  delete+goodbye+close; shared-PSK retain+goodbye+close; trust-none ignore).

### New modules (all commonMain unless noted)

1. `player/sendspin/noise/crypto/` — `NoiseCrypto` interface (dh, aead
   encrypt/decrypt, sha256, hmac, hkdf2/hkdf3, random) + cryptography-kotlin
   implementation. Interface exists so the backend is swappable (libsodium
   fallback) and so the Noise core is testable with fake randomness.
2. `player/sendspin/noise/NoiseProtocol.kt` — `CipherState`, `SymmetricState`,
   `HandshakeState` implementing `Noise_KKpsk2_25519_ChaChaPoly_SHA256`, both
   roles (responder used in production; initiator for loopback tests only —
   vectors, not loopback, are the correctness authority).
   **Amendment (operator-approved):** phase 1 starts with an assessment of
   vendoring `nl.sanderdijkhuis` noise-kotlin (pure-Kotlin, injectable crypto
   interface, snow-vector tests; JVM-only packaging is why it can't be a
   dependency) adapted to our `NoiseCrypto` interface, vs. greenfield. Either
   outcome is acceptable; the vector suite is the gate in both cases and
   provenance is recorded in the noise README.
3. `player/sendspin/noise/SendspinHandshake.kt` — init exchange, raw-byte
   prologue capture, psk_id lookup, inner payload validation, sentinel
   constants, base64url, psk_id derivation, 30 s message timeouts.
4. `player/sendspin/noise/NoiseFraming.kt` — type byte, fragmentation
   encode/decode incl. malformed-sequence rules (protocol error ⇒ close).
5. `player/sendspin/session/SendspinProtocolSession.kt` (+ `LegacySession`,
   `EncryptedSession`) — as above.
6. `player/sendspin/identity/SendspinIdentity.kt` — static X25519 keypair,
   `client_id` derivation; `expect/actual SecureKeyStore` — Android:
   Keystore-backed EncryptedSharedPreferences (`androidx.security:
   security-crypto`, new version-catalog alias; `PlatformContext` reaches the
   Android `actual` by constructor injection through Koin, the existing
   wiring route in `SharedModule`); iOS: Keychain
   (`kSecClassGenericPassword`, replace-semantics writes). Storage loss or
   corruption ⇒ clean regeneration (new identity), never a crash; records
   incompatible with a regenerated identity are reset atomically with it.
7. `player/sendspin/identity/TrustStore.kt` — pairing PSK (CSPRNG, generated
   once), long-term records `{psk, server_id, used}` (stored-pubkey model),
   pre-provisioned shared-PSK record for `record_mode` (random per device),
   enabled flags, unpaired_access flag. Persistence model: **one versioned,
   serialized trust-store blob written per atomic commit** (identity key
   stored separately but versioned with it), not independently updated keys —
   this is what makes "atomic" real on both platforms. Provides the live
   PSK-candidate resolver (sentinel + pairing-if-enabled + records) — config
   mutations must be visible to in-flight candidate lookups immediately.
8. `player/sendspin/pairing/PairingHandler.kt` — pairing-PSK attempt state
   machine (matched-PSK verification, pair-finalize, persist-on-server-ack,
   abort reasons, 2 min attempt timeout, cancel-by-activate) + token minting.
9. `player/sendspin/management/ManagementHandler.kt` — the six commands
   against TrustStore with the full constraint set (see Background),
   one-in-flight rule, `open-pairing-window` ⇒ `invalid`. Storage accounting:
   omitted (unbounded storage; spec permits relying on `storage_exhausted`).

### Changes to existing code

- `model/Messages.kt`: **separate encrypted message models** — `client/init`,
  `server/init`, `noise/handshake`, encrypted `server/hello` (name only),
  encrypted `client/hello` (no clientId/version; adds `trust_level`,
  `supported_pair_methods`, `unpaired_access`), `server/activate`,
  `server/unpair`, pairing messages (`client/pair-finalize`,
  `server/pair-finalize`, `pair/abort`), management messages. Legacy models
  untouched; golden serialization tests pin the legacy wire bytes.
- `protocol/MessageDispatcher.kt`: consumes the session's ordered pump;
  auth handling removed (moved to session); server-hello side effects gated by
  mode as described; routes pairing/management/unpair to handlers; **redacted
  logging** — for protocol messages log only type + length, never raw JSON of
  secret-bearing messages (`set-pairing-config`, `add-record`,
  `pair-finalize`), and drop the current raw-text error/preview logs on the
  encrypted path.
- `SendspinClient.kt`: Connected-reaction rewritten to session events
  (including the auto-resume re-homing described above); audio path consumes
  the session's demuxed audio output instead of `transport.binaryMessages`;
  pairing-activity awareness; re-hello handled inside the session.
- `SendspinConfig` / `SendspinClientFactory`: `encryptionMode`
  (LEGACY | ENCRYPTED | ENCRYPTED_REQUIRED) resolved from
  `ServerInfo.schemaVersion >= 45` + the user toggle; identity/trust stores
  injected. **Factory readiness is a one-shot await:** the session exposes
  `awaitInitialOutcome()` — a deferred completed exactly once per session
  with the first `ProtocolReady` or terminal `Failed` of the initial attach
  (30 s cap, matching the per-message handshake timeout; timeout completes it
  as `Failed`). The WebRTC factory path awaits it before marking the channel
  used; `Failed`/timeout surfaces as `WebRTCSendspinChannelExhausted` and
  stops the client. Later reconnects are ordinary lifecycle events and never
  complete or re-arm this deferred. **`ProtocolReady` (not `Activated`) is
  deliberately the encrypted readiness signal here:** a sentinel session's
  first admissible `server/activate` only arrives after the silent-pairing
  RPC round-trip (or never, if the RPC fails non-fatally), so gating the
  factory on `Activated` would time out every fresh install. Once
  `ProtocolReady` fires the channel has genuinely carried a handshake and is
  no longer virgin, so marking it used is correct; a later activation
  rejection ends in goodbye+close, which over WebRTC is a terminal `Failed`
  and surfaces as channel exhaustion through the same
  `WebRTCSendspinChannelExhausted → forceWebRTCReconnect()` path.
  **Post-ready propagation mechanism:** a session terminal `Failed` arriving
  after `ProtocolReady` on a WebRTC transport is mapped by `SendspinClient`
  to `SendspinState.Error` with `WebRTCSendspinChannelExhausted` as the
  permanent cause on its existing `state` flow — the channel
  `LocalPlayerController` already observes — and the controller routes that
  error to `forceWebRTCReconnect()` exactly as it routes factory-returned
  exhaustion today. **Control-flow invariant:** `LocalPlayerController`
  detects a `SendspinError.Permanent` whose cause is
  `WebRTCSendspinChannelExhausted` *before* its generic Permanent-error
  branch, calls `forceWebRTCReconnect()` exactly once, and skips the generic
  delayed `start()`/retry path for that error (which would otherwise stop and
  retry the same dead wrapper). The controller seam test asserts one
  `forceWebRTCReconnect` call and zero same-wrapper start/retry attempts for
  a post-ready failure. The factory's wrapper-used flag guarantees any retry
  against the same wrapper also returns exhausted, so the wrapper is replaced
  exactly once per failure. ENCRYPTED_REQUIRED + legacy gate ⇒ the
  factory returns a typed failure (`EncryptionRequiredUnavailable`) without
  constructing a transport; `LocalPlayerController` maps it to an unavailable
  player state carrying the explanatory message surfaced in Local Player
  settings/UI.
- `transport/SendspinTransport.kt`: replace the `textMessages`/
  `binaryMessages`/`connectionState` trio with the single ordered
  `Flow<InboundTransportEvent>` described above; both transports and
  `SendspinWsHandler` adapted (epoch published before frames; WebRTC never
  emits a reconnect epoch). **`webrtc/DataChannelWrapper` is a required
  touchpoint:** it currently exposes independent `textMessages`/
  `binaryMessages` flows fed from one receive loop — merging those at the
  transport layer loses source order, so the wrapper itself changes to emit
  one ordered inbound event flow directly from its receive loop, which
  `WebRTCDataChannelTransport` adapts (adding state/epoch events). A
  WebRTC-specific mixed Text→Binary ordering test accompanies this change.
  **`WebRTCConnectionManager` is a consumer of the same wrapper class** (it
  collects the ma-api channel's messages): its collector is adapted to read
  Text events from the new ordered flow. The single-collector rule is
  per-channel-instance — the session is the sole collector of the sendspin
  channel, the connection manager the sole collector of the ma-api channel —
  so no competing collectors exist on either instance.
- MA API layer (`SilentPairingCoordinator`): triggers on the session's
  **`ProtocolReady`** event (hello exchange complete, **pre-activate**) with
  `trustLevel='none'`, sentinel-matched PSK, gate=encrypted, current epoch —
  it must **not** wait for `Activated`/`SendspinState.Ready`: the pairing
  `server/activate` only arrives after the RPC, so waiting would deadlock.
  Calls `sendspin/pair_web_player` with the minted token over the main RPC
  (deduplicated per connection epoch, cancelled on disconnect); non-fatal
  error surfacing. No call for legacy, non-sentinel, or user-trust sessions.
- Settings UI: "Require encryption" toggle in Local Player settings.

### Code comment discipline (non-negotiable)

Code comments must explain behavior in domain terms that stand on their own
in the repository. **Never reference plan-local identifiers — "AC10",
"phase 3", "review round 2", "finding 1", this plan document, etc. — in code
comments, test comments, or KDoc.** Those identifiers are meaningless to
anyone reading the code after this plan is archived. The first subagent who
adds a comment referencing things like "AC10" in code gets drug out behind
the woodshed. Test *names* should describe the behavior under test (e.g.
`legacyAudioFramingIsByteIdentical`), not the criterion number; the AC↔test
mapping lives in this plan and in the PR description only.

## Acceptance Criteria

- **AC1 Mode selection:** schema ≥45 ⇒ first Sendspin frame is `client/init`;
  schema <45 or unknown ⇒ first frame is legacy (`auth` in proxy mode /
  `client/hello`), byte-identical to today. Toggle on + legacy gate ⇒ no
  connection, player unavailable with message.
- **AC2 Noise correctness:** KKpsk2 core passes reference vectors for
  `Noise_KKpsk2_25519_ChaChaPoly_SHA256` (all messages + transport
  ciphertexts); psk_id derivation matches the published sentinel psk_id;
  pairing-token codec matches the spec reference vector.
- **AC3 Handshake driver:** prologue uses exact transmitted bytes; wrong
  psk_id / malformed inner payload / timeout ⇒ socket closed, no app-level
  error, session `Failed`; proxy pre-auth works in both modes.
- **AC4 No message loss:** a server/hello (or any frame) arriving immediately
  after handshake completion is delivered — verified with a deterministic fake
  transport that emits back-to-back frames.
- **AC5 Activation gating:** nothing (state/time/commands) is sent before the
  first admissible `server/activate`; admission-table violations produce the
  spec-mandated goodbye/abort responses (including the messaging.md worked
  example); Ready is emitted on activation.
- **AC6 Re-handshake:** completes mid-session without message interleaving;
  msg 2 under old keys; hello sequence repeats; trust level re-asserted;
  application traffic resumes after the post-re-handshake activate.
- **AC7 Pairing:** fresh install → sentinel session → `pair_web_player` call →
  pairing activate → pair-finalize → record persisted only after
  `server/pair-finalize` → re-handshake to long-term PSK → subsequent
  reconnect matches the long-term record and asserts `trust_level='user'`.
  Cancelling activate / timeout persists nothing. **RPC integration:** exactly
  one `sendspin/pair_web_player` call per connection epoch (deduplicated),
  cancelled on disconnect, no call on legacy/non-sentinel/user-trust
  sessions, failures non-fatal.
- **AC8 Management + unpair:** each command's spec-allowed result-code subset
  and success `data` shape (`list-records.records`, pairing config with PIN
  methods absent), patch semantics (absent fields preserved), constraint set
  enforced; `server/unpair` covers all three branches (stored-pubkey
  delete+goodbye+close / shared retain+goodbye+close / trust-none ignore);
  management on non-management/non-long-term sessions ⇒ `permission_denied`.
- **AC9 Fragmentation:** >65518-byte JSON round-trips; malformed sequences
  close the connection.
- **AC10 Legacy regression:** golden fixtures for legacy auth, client/hello,
  client/state and binary audio framing (including the session's audio
  pass-through) are unchanged by this work.
- **AC11 Reconnect matrix:** WS auto-reconnect re-runs the full handshake with
  epoch ordering (reconnect event before first frame; stale-epoch frames
  dropped); auto-resume fires only on post-reconnect readiness (legacy: Ready;
  encrypted: post-activate) with the attempt-≤9 ladder preserved; WebRTC
  failure is terminal, surfaced as channel exhaustion, and the controller
  negotiates a fresh channel (`forceWebRTCReconnect` path); handshake failure
  over WebRTC does not strand the "channel used" flag.
- **AC12 No secret leakage:** logs never contain a PSK, private key, or
  pairing token (asserted by test with known values).
- **AC13 Identity persistence & recovery:** first load creates and persists
  one keypair + PSKs; subsequent loads yield the same `client_id` and pairing
  token across restarts; missing/corrupt storage regenerates without crash
  and resets incompatible trust records atomically with the new identity.

## Test Strategy

Unit/commonTest (named per AC):
- `NoiseKkpsk2VectorTest` (AC2) — vectors checked into `commonTest/resources`
  (from snow; generated via cacophony if KKpsk2 absent — do this in phase 1).
- `PskIdDerivationTest`, `PairingTokenCodecTest` (AC2).
- `SendspinHandshakeTest` (AC3) — fake transport, byte-exact prologue check,
  failure matrix.
- `FakeSendspinTransport` harness: deterministic, emits the single ordered
  `InboundTransportEvent` stream, records outbound frames, can emit mixed
  Text→Binary frames back-to-back and reconnect epochs followed immediately
  by frames — used by
  `EncryptedSessionTest` (AC3, AC4 incl. mixed back-to-back ordering, AC5,
  AC6, AC11-WS epoch ordering — incl. a stale-epoch frame arriving after a
  new epoch's `Connected` being dropped, and a `Connected` emitted
  synchronously during `connect()` not being lost — plus encrypted
  auto-resume-after-activate and encrypted-audio byte-exactness),
  `LegacySessionGoldenTest` (AC1, AC10 — incl. binary audio pass-through
  golden check and legacy auto-resume-on-Ready), `PairingHandlerTest` (AC7
  protocol half), `ManagementHandlerTest` (AC8; enumerated parameterized
  cases: per-command success `data` shape + allowed result-code subset,
  unsupported PIN fields → `invalid`, patch-preserves-absent-fields, psk_id
  collisions, shared/stored listing + `used` transitions, record-mode
  reference/removal constraints, live candidate update,
  own-record response-before-goodbye, open-window `invalid`),
  `ServerUnpairTest` (AC8; all three branches),
  `NoiseFramingTest` (AC9), `RedactedLoggingTest` (AC12),
  `EncryptionModeGateTest` (AC1 matrix: 44/45/null/toggle),
  `RequireEncryptionUnavailableTest` (AC1 UI half: toggle on + legacy/null
  gate ⇒ factory returns `EncryptionRequiredUnavailable`, no transport is
  constructed, and the controller exposes the unavailable state + message).
- `InboundBackpressureTest` — the producer emits more interleaved Text+Binary
  events than any configured buffer while the consumer is deliberately
  suspended, then resumes; asserts every event arrives exactly once and in
  source order, at both the `DataChannelWrapper` level and the session level
  (via `FakeSendspinTransport`). This test fails if a bounded lossy shared
  flow or `tryEmit`-with-drop delivery is ever reintroduced. **Wrapper test
  seam:** `DataChannelWrapper` currently takes a concrete Ktor
  `WebRtcDataChannel`, which has no common-test fake — so the refactor
  introduces a narrow injected receive-source interface (implemented by the
  Ktor channel adapter in production) that the wrapper's receive loop
  consumes; the wrapper-level half of this test feeds that source directly
  in commonTest while the ordered-output collector is suspended.
- `SendspinIdentityTest` + `TrustStoreRecoveryTest` (AC13) with a fake common
  `SecureKeyStore` (serialization + atomic-recovery logic). Real-`actual`
  platform wiring gets named tests: `SendspinIdentityDeviceTest` in the
  `androidDeviceTest` source set — the module uses
  `com.android.kotlin.multiplatform.library` (AGP 9), where device tests
  require opting in via `withDeviceTestBuilder` (only `withHostTestBuilder`
  is configured today; adding the device-test builder and verifying the
  generated connected-device task name is part of phase 2) — and
  `SendspinIdentityIosTest`
  (run via `:composeApp:iosSimulatorArm64Test`). These validate Koin
  `PlatformContext` injection, EncryptedSharedPreferences, and Keychain
  persistence; they run in the nightly/manual lane (device-dependent —
  recorded as a limitation in Risks), not PR CI.
- Factory/RPC integration seams (AC7 integration half, AC11-WebRTC):
  `SilentPairingCoordinatorTest` with a fake `ServiceClient` — asserts the
  exact `Request("sendspin/pair_web_player", args={"pairing_token": ...})`,
  one call per epoch, disconnect cancellation, non-fatal failure, and no call
  for legacy/non-sentinel/user-trust sessions;
  `SendspinClientFactoryWebRTCTest` — Ready/Failed outcomes, wrapper
  replacement, used/exhausted flag correctness, **and the post-ready path**:
  activation rejection after `ProtocolReady` maps to `SendspinState.Error`
  carrying `WebRTCSendspinChannelExhausted`; plus a controller-level test
  seam proving both factory-returned and post-ready Failed/exhausted trigger
  `forceWebRTCReconnect`.
- Loopback (initiator vs responder) used only as a supplement to vectors.

Integration (documented script, manual + optional CI-nightly):
- `scripts/sendspin-encryption-itest.md` (+ helper script) against a real MA
  dev server (aiosendspin 9.1.0): fresh install → sentinel → silent pair →
  long-term reconnect; proxy and direct modes; `allow_legacy_clients=false`;
  legacy path against current-stable MA. Limitation recorded: device-dependent,
  not part of PR CI.
- Phase-1 device spike (see Phases) is itself a gate: cryptography-kotlin
  X25519/ChaChaPoly/SHA256/HKDF proven on a real Android device and iOS
  device/simulator build before any Noise code is written.

## Review Strategy

Each phase is a separate PR-sized change, reviewed independently. Phase 3
(session refactor) is the highest-risk change and must include the legacy
golden tests in the same PR proving the legacy wire is untouched. Security-
sensitive modules (noise/, identity/) get a focused second review pass against
the spec text, and the Noise core must not be hand-edited after vectors pass
without re-running them (they run in CI commonTest).

Reviewers must also enforce the code comment discipline above: any comment
referencing plan-local identifiers (AC numbers, phase numbers, review rounds)
is a mandatory-fix finding.

## Documentation Strategy

- This plan stays in `docs/plans/` as the design record; update Status on
  landing.
- `player/sendspin/noise/README.md`: protocol-name string, spec section
  mapping, vector provenance (where they came from, how to regenerate).
- CHANGELOG entry + user-facing note for the one-time player identity change
  and the new "Require encryption" setting.

## Phases

1. **Crypto spike + Noise core** — vendoring assessment (noise-kotlin) first;
   add pinned version-catalog aliases + deps
   (`dev.whyoleg.cryptography:cryptography-core:0.6.0`,
   `cryptography-provider-jdk` androidMain, `cryptography-provider-apple`
   iosMain — provider/linker config verified in the spike, including
   compatibility with the Android multiplatform-library plugin; the
   `androidx.security:security-crypto` dependency is validated in this same
   build spike), device spike proving the primitives, then modules 1-4 with
   vector tests. Obtain/generate KKpsk2 vectors. Gate: spike + vectors green.
   No behavior change.
2. **Identity + trust store** — modules 6-7: single-blob atomic persistence,
   Koin `PlatformContext` injection for the Android `actual`, identity
   regeneration/reset semantics, `SendspinIdentityTest`/`TrustStoreRecoveryTest`
   (AC13). No behavior change.
3. **Protocol sessions** — module 5 + dispatcher/client/transport/factory
   refactor: extract `LegacySession` (with golden tests in the same PR,
   including audio pass-through and auto-resume re-homing),
   add `EncryptedSession` behind a debug-only flag (gate forced LEGACY).
4. **Pairing** — module 8 + MA-API silent-pairing coordinator + readiness
   contract for the factory/WebRTC, including the exhaustion→
   `forceWebRTCReconnect` surfacing and the test seams for
   `SilentPairingCoordinatorTest`/`SendspinClientFactoryWebRTCTest`.
5. **Management + unpair** — module 9 + `server/unpair`.
6. **Gate + settings** — `schemaVersion >= 45` gate live, "require
   encryption" toggle, redacted-logging sweep, integration script run against
   dev server + current-stable server; enable by default.

## Risks, Blockers, and Required Decisions

- **cryptography-kotlin pre-1.0** (single maintainer, no audit); mitigated by
  the `NoiseCrypto` interface (libsodium bindings as drop-in fallback) and by
  the fact it wraps OS-native crypto (JDK/CryptoKit) rather than
  reimplementing primitives. Phase-1 spike is the go/no-go.
- **KKpsk2 vectors availability** — if absent from snow's set, generating via
  cacophony adds a small Haskell tooling chore (documented in the noise
  README).
- **Server 'web player' heuristic**: silent pairing depends on
  `is_web_player` device-info classification (marked TODO server-side). If the
  server adopts an explicit flag, switch to it.
- **WebRTC frame sizing** vs fragmentation: covered by AC9/AC11 tests, but
  real-channel behavior verified in the phase-6 integration run.
- **Platform identity tests are device-dependent** (Android instrumented +
  iOS simulator) and run nightly/manually, not in PR CI; the common
  fake-store tests cover logic but not platform-wiring regressions between
  those runs.
- **Keystore/Keychain loss** (reinstall/restore) ⇒ new identity; acceptable
  per decision 2, must regenerate cleanly (tested in `SendspinIdentityTest`).
- **`settings.sendspinClientId` becomes vestigial in encrypted mode** (the
  X25519 pubkey is the identity); phase 6 should note this in the settings UI
  or code without removing the legacy path's use of it.
