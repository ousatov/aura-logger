package com.usatov.aura.logger.demo.debug

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** MVI contract of the debug time-shift tool in Settings (debug builds only). */
object TimeShiftContract {

    /** Shift steps offered as buttons: -1 day, -1 hour, +1 hour, +1 day. */
    val STEPS: List<Duration> = listOf((-1).days, (-1).hours, 1.hours, 1.days)

    /**
     * @property offset current offset of [TimeShiftClock].
     * @property shiftedNow the shifted current time, `DD/MM/YYYY HH:MM:SS` in the device time zone.
     */
    data class State(
        val offset: Duration = Duration.ZERO,
        val shiftedNow: String = "",
    )

    sealed interface Intent {
        /** Shifts the clock by [delta]. */
        data class ShiftBy(val delta: Duration) : Intent

        /** Resets the offset to zero. */
        data object Reset : Intent
    }

    sealed interface Effect
}
