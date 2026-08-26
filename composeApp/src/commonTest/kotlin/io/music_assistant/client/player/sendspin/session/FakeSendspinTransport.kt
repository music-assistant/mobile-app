package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Deterministic transport for session tests: inbound events are injected via
 * [emit] (including mixed back-to-back Text/Binary frames and reconnect
 * epochs), outbound frames are captured on [textOut]/[binaryOut] so an
 * in-test peer can react to them.
 */
class FakeSendspinTransport : SendspinTransport {
    private val eventsChannel = Channel<InboundTransportEvent>(Channel.UNLIMITED)
    override val events: Flow<InboundTransportEvent> = eventsChannel.receiveAsFlow()

    val textOut = Channel<String>(Channel.UNLIMITED)
    val binaryOut = Channel<ByteArray>(Channel.UNLIMITED)

    val sentTexts = mutableListOf<String>()
    val sentBinaries = mutableListOf<ByteArray>()

    var connectCount = 0
        private set
    var disconnectCount = 0
        private set
    var closed = false
        private set

    /**
     * Invoked from [connect]; typically emits `Connected` synchronously to
     * prove a subscription established before connect() cannot lose it.
     */
    var onConnect: (FakeSendspinTransport) -> Unit = {
        it.emit(InboundTransportEvent.Connected(epoch = 1, isReconnect = false))
    }

    fun emit(event: InboundTransportEvent) {
        eventsChannel.trySend(event)
    }

    override suspend fun connect() {
        connectCount++
        onConnect(this)
    }

    override suspend fun sendText(message: String) {
        sentTexts.add(message)
        textOut.trySend(message)
    }

    override suspend fun sendBinary(data: ByteArray) {
        sentBinaries.add(data)
        binaryOut.trySend(data)
    }

    override suspend fun disconnect() {
        disconnectCount++
    }

    override fun close() {
        closed = true
    }
}
