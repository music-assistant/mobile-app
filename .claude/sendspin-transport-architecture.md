# Sendspin Transport Architecture

**Last Updated:** 2026-02-13

## Overview

Sendspin protocol abstracted from transport layer via `SendspinTransport` interface. Enables audio streaming over WebSocket (direct) or WebRTC data channel (remote).

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      MainDataSource                          │
│  - Monitors session state                                    │
│  - Calls SendspinClientFactory when Sendspin enabled         │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                 SendspinClientFactory                        │
│  - Detects connection type (WebRTC vs WebSocket)            │
│  - Creates appropriate transport                            │
│  - Starts SendspinClient with transport                      │
└────────────────────────────┬────────────────────────────────┘
                             │
            ┌────────────────┴────────────────┐
            │ if webrtcSendspinChannel exists │ else
            ▼                                  ▼
┌───────────────────────────┐      ┌───────────────────────────┐
│ WebRTCDataChannelTransport│      │ WebSocketSendspinTransport│
│  - Wraps DataChannelWrapper│      │  - Wraps SendspinWsHandler│
│  - Maps state to WebSocket │      │  - WebSocket connection   │
│  - Waits for channel open  │      │  - Auto-reconnect         │
└──────────┬────────────────┘      └──────────┬────────────────┘
           │                                   │
           └────────────┬──────────────────────┘
                        │ SendspinTransport interface
                        ▼
           ┌────────────────────────────┐
           │      SendspinClient         │
           │  - Protocol orchestration  │
           │  - Uses transport.connect()│
           │  - MessageDispatcher       │
           │  - AudioPipeline           │
           └────────────────────────────┘
```

---

## Interface Definition

```kotlin
interface SendspinTransport {
    val connectionState: Flow<WebSocketState>
    val textMessages: Flow<String>       // JSON protocol messages
    val binaryMessages: Flow<ByteArray>  // Audio chunks

    suspend fun connect()
    suspend fun sendText(message: String)
    suspend fun sendBinary(data: ByteArray)
    suspend fun disconnect()
    fun close()
}
```

**Key design:** Transport provides flows for incoming messages, suspend functions for outgoing.

---

## Implementations

### 1. WebSocketSendspinTransport

**Purpose:** WebSocket connection to `/sendspin` endpoint (direct or proxy mode)

**Wraps:** `SendspinWsHandler` (existing WebSocket handler)

**Features:**
- Auto-reconnect with exponential backoff
- Aggressive keepalive (5s ping)
- Token authentication (proxy mode)
- Network resilience

**When used:** When NOT connected via WebRTC (direct connection to server)

---

### 2. WebRTCDataChannelTransport

**Purpose:** WebRTC data channel for Sendspin audio streaming

**Wraps:** `DataChannelWrapper` (WebRTC data channel abstraction)

**Features:**
- State mapping: `DataChannelState` → `WebSocketState`
- Waits for channel to open (10s timeout)
- Text messages for protocol, binary for audio
- No reconnection (WebRTC handles it)

**When used:** When connected via WebRTC remote access

**Key difference:** Channel already created before transport instantiation. `connect()` just waits for it to open.

---

## Factory Auto-Detection Logic

```kotlin
// SendspinClientFactory.createIfEnabled()

val webrtcChannel = serviceClient.webrtcSendspinChannel

if (webrtcChannel != null) {
    // WebRTC mode
    val webrtcConfig = config.copy(
        mainConnectionPort = null,  // requiresAuth = false
        authToken = null            // Auth inherited from main channel
    )
    val client = SendspinClient(webrtcConfig, mediaPlayerController)
    val transport = WebRTCDataChannelTransport(webrtcChannel)
    client.connectWithTransport(transport)
} else {
    // WebSocket mode
    val client = SendspinClient(config, mediaPlayerController)
    client.start()  // Creates WebSocketSendspinTransport internally
}
```

**Key decisions:**
1. Factory is responsible for detecting transport type
2. Factory creates and starts client (returns pre-started)
3. Config is modified for WebRTC to skip auth
4. Caller (MainDataSource) doesn't need to know transport type

---

## WebRTC Channel Lifecycle

### Creation (WebRTCConnectionManager)

```kotlin
// In handleConnected(), BEFORE createOffer()

val maApiChannel = pc.createDataChannel("ma-api")
setupDataChannel(maApiChannel, sessionId)

val sendspinChannel = pc.createDataChannel("sendspin")
setupSendspinDataChannel(sendspinChannel)

// Now create offer (includes both channels in SDP)
val offer = pc.createOffer()
```

**Why both created?**
- Channels must be in SDP offer
- Creating them later requires renegotiation
- Unused channels are cheap (no data flows)

### Exposure (ServiceClient)

```kotlin
val webrtcSendspinChannel: DataChannelWrapper?
    get() = webrtcManager?.sendspinDataChannel
```

**Public property** allows SendspinClientFactory to check if WebRTC channel exists.

---

## State Management

### WebSocketState (Sendspin Protocol)

```kotlin
sealed class WebSocketState {
    object Disconnected
    object Connecting
    data class Reconnecting(val attempt: Int)
    object Connected
    data class Error(val error: Throwable)
}
```

### DataChannelState (WebRTC)

From `webrtc-kmp` library:
- `Connecting` - Channel being established
- `Open` - Channel ready
- `Closing` - Channel shutting down
- `Closed` - Channel closed

### Mapping (WebRTCDataChannelTransport)

```kotlin
dataChannelWrapper.state.map { dataChannelState ->
    when (dataChannelState) {
        DataChannelState.Connecting -> WebSocketState.Connecting
        DataChannelState.Open -> WebSocketState.Connected
        DataChannelState.Closing -> WebSocketState.Disconnected
        DataChannelState.Closed -> WebSocketState.Disconnected
    }
}
```

---

## Authentication Flow

### WebSocket Mode (Proxy)

```
1. Connect WebSocket → ws://host:8095/sendspin
2. Send auth message: { "type": "auth", "token": "...", "client_id": "..." }
3. Receive auth_ok: { "type": "auth_ok" }
4. Send client/hello
5. Receive server/hello
6. Start streaming
```

### WebRTC Mode

```
1. WebRTC peer connection established (auth on "ma-api" channel)
2. "sendspin" data channel opens
3. Send client/hello (NO AUTH - inherited from main channel)
4. Receive server/hello
5. Start streaming
```

**Critical:** WebRTC sendspin channel does NOT send auth. Auth is connection-level, not channel-level.

---

## Message Flow

### Protocol Messages (Text/JSON)

```
SendspinClient
    ↓ sendText("{ ... }")
MessageDispatcher
    ↓
SendspinTransport (interface)
    ↓
WebRTCDataChannelTransport OR WebSocketSendspinTransport
    ↓
Network (WebRTC data channel or WebSocket)
    ↓
Server
```

### Audio Chunks (Binary)

```
Server
    ↓
Network (WebRTC data channel or WebSocket)
    ↓
SendspinTransport.binaryMessages (Flow<ByteArray>)
    ↓
AudioStreamManager.collect { chunk -> ... }
    ↓
AudioDecoder (Opus/FLAC/PCM)
    ↓
MediaPlayerController (AudioTrack on Android)
    ↓
Speaker output
```

---

## Error Handling

### Transport Connection Failure

```kotlin
try {
    client.connectWithTransport(transport)
} catch (e: Exception) {
    // Factory returns Result.failure
    // MainDataSource logs and skips Sendspin
}
```

### Channel Open Timeout (WebRTC)

```kotlin
// WebRTCDataChannelTransport.connect()
val openState = withTimeoutOrNull(10_000) {
    dataChannelWrapper.state.first { it == DataChannelState.Open }
}
if (openState == null) {
    throw IllegalStateException("Channel did not open within timeout")
}
```

### Runtime Errors

- WebSocket: Auto-reconnect handles transient failures
- WebRTC: Peer connection handles reconnection, channels survive

---

## Testing Strategy

### Unit Tests (Not yet implemented)

- Mock `SendspinTransport` for `SendspinClient` tests
- Mock `DataChannelWrapper` for `WebRTCDataChannelTransport` tests
- Test state mapping edge cases

### Integration Tests

- [ ] Direct WebSocket connection
- [ ] WebRTC connection with Sendspin
- [x] Player registration (verified working)
- [x] Playback start (verified working)
- [ ] Playback quality (bugs exist)
- [ ] Transport switching (Direct ↔ WebRTC)

---

## Performance Considerations

### WebSocket
- **Latency:** ~50-100ms base
- **Bandwidth:** Same as WebRTC (codec-dependent)
- **Reliability:** TCP, ordered delivery guaranteed
- **Reconnection:** Automatic with exponential backoff

### WebRTC Data Channel
- **Latency:** ~100-200ms base (DTLS/SRTP overhead)
- **Bandwidth:** Same as WebSocket (codec-dependent)
- **Reliability:** Ordered delivery (`ordered=true`)
- **Reconnection:** Handled by peer connection

**Trade-off:** Slightly higher latency for remote access convenience.

---

## Future Enhancements

1. **Metrics collection:** Track latency, drops, buffer health per transport
2. **Transport quality reporting:** Expose transport-specific stats to UI
3. **Dynamic transport switching:** Switch without disconnecting
4. **WebRTC fallback:** Auto-switch to WebSocket if WebRTC quality poor
5. **Multi-transport support:** Use both simultaneously for redundancy

---

## References

- **Sendspin Status:** `.claude/sendspin-status.md`
- **WebRTC Sendspin Status:** `.claude/sendspin-webrtc-status.md`
- **WebRTC Implementation:** `.claude/webrtc-implementation-plan.md`
- **Transport Interface:** `composeApp/src/commonMain/kotlin/io/music_assistant/client/player/sendspin/transport/SendspinTransport.kt`
