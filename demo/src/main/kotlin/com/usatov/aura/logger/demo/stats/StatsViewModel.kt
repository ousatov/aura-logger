package com.usatov.aura.logger.demo.stats

import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.stats.StatsContract.Effect
import com.usatov.aura.logger.demo.stats.StatsContract.DayCount
import com.usatov.aura.logger.demo.stats.StatsContract.Intent
import com.usatov.aura.logger.demo.stats.StatsContract.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Statistics section. Loads `EventTracker.getStats()` on [Intent.Visible] and then every
 * [StatsContract.REFRESH_INTERVAL] until [Intent.Hidden]; repeated [Intent.Visible] does not start
 * a second refresh loop. "Today" and the 7-day window use [clock] and [zoneProvider] at refresh time;
 * a day belongs to the window if `DailyEventCount.latestTimestamp` falls on one of the last 7 days.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val tracker: EventTracker,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : MviViewModel<State, Intent, Effect>(State()) {

    private var refreshJob: Job? = null

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Visible -> startRefreshing()
            Intent.Hidden -> stopRefreshing()
        }
    }

    private fun startRefreshing() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(StatsContract.REFRESH_INTERVAL)
            }
        }
    }

    private fun stopRefreshing() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private suspend fun refresh() {
        try {
            val stats = tracker.getStats()
            val zone = zoneProvider.zone()
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
            val from = today.minusDays((StatsContract.DAYS_SHOWN - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val until = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val days = stats.dailyCounts
                .filter { it.latestTimestamp in from until until }
                .map { DayCount(it.date, it.count) }
            setState {
                copy(isLoading = false, total = stats.totalCount, today = stats.todayCount, days = days, loadFailed = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // isLoading is kept: until the first success nothing is loaded, so "empty" must not be claimed.
            setState { copy(loadFailed = true) }
        }
    }
}
