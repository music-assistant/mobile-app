# WebRTC Remote Access

Remote Access connects the app to a Music Assistant server through a WebRTC peer connection.
It works on Android and iOS. The server needs no open port.

## Library and wrappers

- The library is `io.ktor:ktor-client-webrtc`, with the Ktor version (see `.claude/dependencies.md`).
- All code is in `commonMain`, package `webrtc/`. There is no expect or actual declaration.
- `PeerConnectionWrapper` and `DataChannelWrapper` are plain classes over the Ktor types.

## Connection

- `SignalingClient` connects to `wss://signaling.music-assistant.io/ws` (`DEFAULT_SIGNALING_URL`).
- `WebRTCConnectionManager` sends the Remote ID, gets the ICE servers, sends the SDP offer and exchanges
  the ICE candidates.
- An ICE `DISCONNECTED` state gets a grace time of 4 s (`ICE_DISCONNECT_GRACE_MS`) before the manager
  reports an error. `FAILED` and `CLOSED` report the error at once.
- `api/WebRTCTransport.kt` owns one manager for each connection and restarts it after an error.
  It uses `runReconnectionLoop` from `api/ReconnectBackoff.kt`, with `DEFAULT_MAX_RECONNECT_ATTEMPTS` (20).
  The delays are 0, 0.5, 1, 2, 4, 8, 15, 30 and 60 s, then 120 s. The loop waits for the network
  if it is down.
- `api/SessionMachine.kt` holds the session lifecycle, and `api/KtorServiceClient.kt` runs it.
  See the Session Lifecycle section of `.claude/architecture.md`.
- The token of each server is stored under its `server_id` (`SettingsRepository.getTokenForServer`).
  An address key is not safe, because one address can host different servers over time.

## Data channels

| Label | Opened | Settings | Content |
|-------|--------|----------|---------|
| `ma-api` | Before the offer | `ordered = true`, `maxRetransmits = -1` | JSON API commands and events, as TEXT messages |
| `sendspin` | Before the offer, then again in-band | `ordered = true` | Sendspin audio and control |
| `http_proxy` | In-band, from schema 49 | `ordered = true` | Artwork and other HTTP requests |

- The manager creates `ma-api` and `sendspin` before the offer, because a data channel adds the
  `m=application` section to the SDP.
- The `sendspin` channel is reliable and ordered. Unreliable delivery lost about 15% of the packets and
  made audible distortion. The Opus framing needs every packet.
- A channel that opens later is signalled in-band on the live peer, with no SDP renegotiation.
  `openHttpProxyChannel` and `openDataChannel` wait 10 s (`CHANNEL_OPEN_TIMEOUT_MS`) for the open state.

## Messages

- `DataChannelWrapper.send(String)` sends a TEXT message, and `sendBinary` sends a BINARY message.
  The server reads the API messages as text, so send API JSON with `send(String)` only.
- The receive side puts text and binary messages into one ordered `inbound` stream with one collector.
  The buffer is `Channel.UNLIMITED`. Do not add a drop policy, because a lost message breaks the protocol
  state of the consumer.
- Channel state changes come from the connection events, not from a poll. A poll added a delay that broke
  the first auth handshake.
- The server splits a message above the 256 KiB limit of libdatachannel into `__chunk__` envelopes.
  `WebRTCChunkReassembler` (`api/WebRTCTransport.kt`) joins them. A whole message passes through.

## HTTP proxy

- `WebRTCHttpProxy` sends HTTP requests over a data channel, with the protocol of the web frontend.
  The artwork loader uses it for `mawebrtc://` URLs (see Artwork Loading in `.claude/architecture.md`).
- From schema 49 (`LABEL_ROUTED_CHANNELS_SCHEMA`), the transport opens `http_proxy` after `server_info`.
  The body comes as raw binary messages after a small JSON header.
- An older server gets the requests on `ma-api`, with a hex body. A server before schema 49 drops the
  whole session for an unknown label, so the transport never opens `http_proxy` before it knows the schema.
- If `http_proxy` does not open, the requests stay on `ma-api`.
- A semaphore limits the requests in flight to 6, so artwork does not block the API messages.

## Sendspin over WebRTC

- `LocalPlayerEndpoints` gives `Endpoint.WebRtc` for a WebRTC session. Each Sendspin connection attempt
  gets a new channel from `openFreshChannel`:
  1. The negotiated `sendspin` channel, if no attempt used it.
  2. From schema 49, a new `sendspin` channel on the live peer. The spent channel must close first,
     because the server accepts one `sendspin` channel for each peer.
  3. On an older server, or if the new channel does not open, a renegotiation
     (`SessionEvent.ForceWebRTCReconnect`). This interrupts the MA session.
- See the Sendspin section of `.claude/architecture.md` for the player side.

## Rules

- Keep one owner for each coroutine job, and cancel it explicitly. `return@collect` does not cancel the
  `launch` that holds the collection.
- Start a reconnection in its own job. Do not start it as a child of the job that the reconnection cancels.
- Run one reconnection loop at a time. Cancel the old `reconnectionJob` before a new loop starts.
- Read the data for a reconnection before the state changes, because a different collector can change the
  state first.
- Never gate a new channel label on a guess. Read the schema version first.
