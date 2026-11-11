package com.usatov.aura.logger.demo.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Debug-only clock: system time plus a user-controlled [offset], passed to the SDK at init so
 * that grouping by day, "today" and retention can be checked without waiting. Thread-safe.
 * The offset lives in memory only and is zero after a process restart.
 *
 * [systemClock] is the base clock (system clock in the app, a fake in tests).
 */
@Singleton
class TimeShiftClock @Inject constructor() : Clock {

    internal var systemClock: Clock = Clock.System

    private val mutableOffset = MutableStateFlow(Duration.ZERO)

    /** Current offset from the system time; can be negative. Zero initially. */
    val offset: StateFlow<Duration> = mutableOffset.asStateFlow()

    /** Adds [delta] (may be negative) to [offset]. */
    fun shiftBy(delta: Duration) {
        mutableOffset.update { it + delta }
    }

    /** Sets [offset] back to zero. */
    fun reset() {
        mutableOffset.value = Duration.ZERO
    }

    override fun now(): Instant = systemClock.now() + mutableOffset.value
}
