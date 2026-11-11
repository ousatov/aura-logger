package com.usatov.aura.logger.demo.actions

import androidx.lifecycle.viewModelScope
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.actions.ActionsContract.Effect
import com.usatov.aura.logger.demo.actions.ActionsContract.Intent
import com.usatov.aura.logger.demo.actions.ActionsContract.State
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Test action buttons. See [ActionsContract]. */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val appEvents: AppEvents,
    private val tracker: EventTracker,
) : MviViewModel<State, Intent, Effect>(State()) {

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.TrackEvent -> {
                appEvents.testEvent()
                sendEffect(Effect.Tracked(1))
            }
            Intent.TrackHundredEvents -> {
                appEvents.testEvents(ActionsContract.STRESS_COUNT)
                sendEffect(Effect.Tracked(ActionsContract.STRESS_COUNT))
            }
            Intent.TrackWithProperties -> {
                appEvents.testEventWithProperties()
                sendEffect(Effect.Tracked(1))
            }
            Intent.ClearRequested -> if (!currentState.isClearing) {
                setState { copy(confirmClear = true) }
            }
            Intent.ClearConfirmed -> clearAll()
            Intent.ClearDismissed -> setState { copy(confirmClear = false) }
        }
    }

    private fun clearAll() {
        if (currentState.isClearing) return
        setState { copy(isClearing = true, confirmClear = false) }
        viewModelScope.launch {
            try {
                val deleted = tracker.clearAll()
                appEvents.eventsCleared(deleted)
                sendEffect(Effect.Cleared(deleted, kept = null))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                sendEffect(Effect.ClearFailed)
            } finally {
                setState { copy(isClearing = false) }
            }
        }
    }
}
