package io.music_assistant.client.utils

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [this] seen through [transform]. Unlike `map { }.stateIn(scope)`, [StateFlow.value] is never
 * behind the source: it is computed on read, so code that writes the source and then reads the
 * view sees its own write. [transform] must be a cheap pure function, because every read runs it.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> = object : StateFlow<R> {
    override val value: R get() = transform(this@mapState.value)
    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        this@mapState.map(transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

/**
 * [first] and [second] seen through [transform], with the same read-on-demand [StateFlow.value]
 * as [mapState]: a write to either source is visible on the next read.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <A, B, R> combineState(
    first: StateFlow<A>,
    second: StateFlow<B>,
    transform: (A, B) -> R,
): StateFlow<R> = object : StateFlow<R> {
    override val value: R get() = transform(first.value, second.value)
    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        combine(first, second, transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
