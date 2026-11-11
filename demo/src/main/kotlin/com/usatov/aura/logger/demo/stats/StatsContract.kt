package com.usatov.aura.logger.demo.stats

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** MVI contract of the statistics section on the Dashboard. */
object StatsContract {

    /** Statistics are reloaded this often while the section is visible. */
    val REFRESH_INTERVAL: Duration = 3.seconds

    /** Number of calendar days shown in [State.days]: today and the 6 days before. */
    const val DAYS_SHOWN: Int = 7

    /**
     * @property isLoading no statistics loaded yet.
     * @property total total number of stored events.
     * @property today events tracked today.
     * @property days events per day for the last [DAYS_SHOWN] calendar days (device time zone),
     * newest first; only days with events; days outside that window are not shown.
     * @property loadFailed the last refresh failed; the previous values are kept.
     */
    data class State(
        val isLoading: Boolean = true,
        val total: Int = 0,
        val today: Int = 0,
        val days: List<DayCount> = emptyList(),
        val loadFailed: Boolean = false,
    ) {
        /** "No events tracked" is shown when loaded and there are no events. */
        val isEmpty: Boolean get() = !isLoading && total == 0
    }

    /** @property date `DD/MM/YYYY`, as stored by the SDK. Displayed as "`date` - `count`". */
    data class DayCount(val date: String, val count: Int)

    sealed interface Intent {
        /** The section became visible (lifecycle resumed): load now, then every [REFRESH_INTERVAL]. */
        data object Visible : Intent

        /** The section is no longer visible (lifecycle paused): stop refreshing. */
        data object Hidden : Intent
    }

    sealed interface Effect
}
