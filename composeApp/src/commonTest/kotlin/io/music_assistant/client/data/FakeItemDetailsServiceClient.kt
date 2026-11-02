package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeItemDetailsServiceClient : StubServiceClient() {

    override val sessionState: StateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)

    override val isReadyForCommands: StateFlow<Boolean> =
        MutableStateFlow(false)

    override val externalConsumerActive: StateFlow<Boolean> =
        MutableStateFlow(false)

    override val events = emptyFlow<io.music_assistant.client.data.model.server.events.Event<out Any>>()

    override val foregroundEvents = emptyFlow<Unit>()
}
