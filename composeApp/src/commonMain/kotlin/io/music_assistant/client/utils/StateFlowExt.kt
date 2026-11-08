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
 * [this] and [other] seen through [transform], with the same read-on-demand [StateFlow.value]
 * as [mapState]: a write to either source is visible on the next read.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <A, B, R> StateFlow<A>.combineState(
    other: StateFlow<B>,
    transform: (A, B) -> R,
): StateFlow<R> = object : StateFlow<R> {
    override val value: R get() = transform(this@combineState.value, other.value)
    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        combine(this@combineState, other, transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
