package io.music_assistant.client.webrtc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks in that close() always finishes its teardown. WebRTCConnectionManager's
 * cleanup() cancels the job it runs in before closing the channels, so close()
 * is routinely called from an already-cancelled coroutine. If the flush wait
 * then aborts the rest of close(), the wrapper is left refusing sends while
 * still reporting Open with a live inbound — a consumer such as the Sendspin
 * transport never learns the channel died and never reconnects.
 */
class DataChannelWrapperCloseTest {
    /** An open channel with nothing to deliver. */
    private class SilentReceiveSource : DataChannelReceiveSource {
        override suspend fun receive(): DataChannelInbound = awaitCancellation()
    }

    @Test
    fun closeFromCancelledCallerStillClosesChannel() = runTest {
        withContext(Dispatchers.Default) {
            val wrapper = DataChannelWrapper(
                dataChannel = null,
                connectionEvents = null,
                receiveSource = SilentReceiveSource(),
                initialState = DataChannelState.Open,
                label = "test",
                diagnostics = WebRTCDiagnostics(),
            )
            val inbound = wrapper.inbound.produceIn(this)

            launch {
                cancel()
                wrapper.close()
            }.join()

            assertEquals(DataChannelState.Closed, wrapper.state.value)
            assertTrue(withTimeout(5_000) { inbound.receiveCatching() }.isClosed)
            coroutineContext[Job]?.cancelChildren()
        }
    }
}
