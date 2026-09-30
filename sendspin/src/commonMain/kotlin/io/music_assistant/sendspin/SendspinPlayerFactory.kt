package io.music_assistant.sendspin

import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.SendspinPlayer
import io.music_assistant.sendspin.player.SendspinPlayerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Creates the local Sendspin player. It runs for the life of [scope]; a `null`
 * [config] disables it. Keep the same [io.music_assistant.sendspin.api.Endpoint]
 * instance across config updates unless the endpoint really changed, because
 * an endpoint change restarts the connection.
 */
fun SendspinPlayer(config: StateFlow<LocalPlayerConfig?>, deps: SendspinDeps, scope: CoroutineScope): SendspinPlayer =
    SendspinPlayerImpl(config, deps, scope)
