package com.usatov.aura.logger.demo.stats

import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.stats.StatsContract.Effect
import com.usatov.aura.logger.demo.stats.StatsContract.Intent
import com.usatov.aura.logger.demo.stats.StatsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock

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

    override fun onIntent(intent: Intent): Unit = TODO("D2")
}
