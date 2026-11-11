package com.usatov.aura.logger.demo.actions

/** MVI contract of the test action buttons on the Dashboard. */
object ActionsContract {

    /** Number of events tracked by "Track 100 Events". */
    const val STRESS_COUNT: Int = 100

    /**
     * @property isClearing "Clear All Events" is running; the clear button is disabled.
     * @property confirmClear the clear confirmation dialog is shown.
     */
    data class State(
        val isClearing: Boolean = false,
        val confirmClear: Boolean = false,
    )

    sealed interface Intent {
        /** "Track Event": one `AppEvents.testEvent`. */
        data object TrackEvent : Intent

        /** "Track 100 Events": `AppEvents.testEvents(STRESS_COUNT)`. */
        data object TrackHundredEvents : Intent

        /** "Track with Properties": `AppEvents.testEventWithProperties`. */
        data object TrackWithProperties : Intent

        /** "Clear All Events" tapped: show the confirmation. Ignored while clearing. */
        data object ClearRequested : Intent

        /**
         * Confirmed: hide the dialog, run `EventTracker.clearAll()`, then track
         * `AppEvents.eventsCleared(deleted)`. Ignored while clearing.
         */
        data object ClearConfirmed : Intent

        /** Dismissed: hide the dialog. */
        data object ClearDismissed : Intent
    }

    sealed interface Effect {
        /** Shown after a track action; [count] events were tracked. */
        data class Tracked(val count: Int) : Effect

        /** Shown after clearing; unseen events may be kept by the SDK, so [deleted] can be less than the total. */
        data class Cleared(val deleted: Int) : Effect

        /** Clearing failed. */
        data object ClearFailed : Effect
    }
}
