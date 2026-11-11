package com.usatov.aura.logger.demo.debug

import androidx.lifecycle.viewModelScope
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Effect
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Intent
import com.usatov.aura.logger.demo.debug.TimeShiftContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaInstant
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Debug time-shift tool; state follows [TimeShiftClock.offset]. See [TimeShiftContract]. */
@HiltViewModel
class TimeShiftViewModel @Inject constructor(
    private val clock: TimeShiftClock,
    private val zoneProvider: ZoneProvider,
) : MviViewModel<State, Intent, Effect>(State()) {

    init {
        viewModelScope.launch {
            clock.offset.collect { offset -> setState { copy(offset = offset, shiftedNow = formatNow()) } }
        }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.ShiftBy -> clock.shiftBy(intent.delta)
            Intent.Reset -> clock.reset()
        }
    }

    private fun formatNow(): String =
        FORMATTER.format(ZonedDateTime.ofInstant(clock.now().toJavaInstant(), zoneProvider.zone()))

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    }
}
