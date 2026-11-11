package com.usatov.aura.logger.demo.debug

import kotlinx.coroutines.flow.StateFlow
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

    /** Current offset from the system time; can be negative. Zero initially. */
    val offset: StateFlow<Duration>
        get() = TODO("D6")

    /** Adds [delta] (may be negative) to [offset]. */
    fun shiftBy(delta: Duration): Unit = TODO("D6")

    /** Sets [offset] back to zero. */
    fun reset(): Unit = TODO("D6")

    override fun now(): Instant = TODO("D6")
}
