package com.usatov.aura.logger.demo.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base of every screen ViewModel (hand-rolled MVI).
 *
 * - The UI renders [state] and sends user actions with [onIntent]; it never changes state itself.
 * - State changes go through [setState] with a pure reducer `S.() -> S`; keep side effects
 *   (SDK calls, timers) in [onIntent] and launched coroutines.
 * - One-off events (messages, navigation) are sent with [sendEffect] and collected once from
 *   [effects]. Effects sent while nobody collects are buffered and delivered to the next collector.
 *
 * Thread-safe: [setState] and [sendEffect] can be called from any thread.
 */
abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /** Current screen state. */
    val state: StateFlow<S> = mutableState.asStateFlow()

    /** One-off effects, each delivered to exactly one collector. */
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Handles a user action. Must not block. */
    abstract fun onIntent(intent: I)

    /** The latest state. */
    protected val currentState: S
        get() = mutableState.value

    /** Atomically replaces the state with the result of the pure [reducer]. */
    protected fun setState(reducer: S.() -> S) {
        mutableState.update(reducer)
    }

    /** Emits a one-off [effect]. Never suspends; effects beyond the buffer are dropped. */
    protected fun sendEffect(effect: E) {
        effectChannel.trySend(effect)
    }
}
