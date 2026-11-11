package com.usatov.aura.logger.demo.debug

import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Effect
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Intent
import com.usatov.aura.logger.demo.debug.TimeShiftContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Debug time-shift tool; state follows [TimeShiftClock.offset]. See [TimeShiftContract]. */
@HiltViewModel
class TimeShiftViewModel @Inject constructor(
    private val clock: TimeShiftClock,
    private val zoneProvider: ZoneProvider,
) : MviViewModel<State, Intent, Effect>(State()) {

    override fun onIntent(intent: Intent): Unit = TODO("D6")
}
