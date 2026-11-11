package com.usatov.aura.logger.demo.config

import com.usatov.aura.logger.core.EventTrackerConfig

/**
 * MVI contract of the config section in Settings: retention period and max events.
 *
 * Inputs are kept as text so that the user can type freely; they are validated on every change.
 */
object ConfigContract {

    /** Accepted range of the retention period, days. */
    val RETENTION_DAYS_RANGE: IntRange = 1..3650

    /** Accepted range of the max events limit. */
    val MAX_EVENTS_RANGE: IntRange = 1..100_000

    /**
     * @property retentionInput text of the retention field; filled from the stored config once loaded.
     * @property maxEventsInput text of the max events field; filled from the stored config once loaded.
     * @property retentionError validation error of [retentionInput], `null` when valid.
     * @property maxEventsError validation error of [maxEventsInput], `null` when valid.
     * @property savedConfig the stored config, `null` until loaded.
     * @property isSaving a save is in progress.
     */
    data class State(
        val retentionInput: String = "",
        val maxEventsInput: String = "",
        val retentionError: InputError? = null,
        val maxEventsError: InputError? = null,
        val savedConfig: EventTrackerConfig? = null,
        val isSaving: Boolean = false,
    ) {
        /**
         * Save is enabled when the config is loaded, both inputs are valid, nothing is being saved
         * and the inputs differ from [savedConfig].
         */
        val canSave: Boolean
            get() = savedConfig != null && retentionError == null && maxEventsError == null && !isSaving &&
                (retentionInput.trim() != savedConfig.retentionDays.toString() ||
                    maxEventsInput.trim() != savedConfig.maxEvents.toString())
    }

    /** Why an input is invalid. Leading and trailing whitespace is ignored. */
    enum class InputError { EMPTY, NOT_A_NUMBER, OUT_OF_RANGE }

    sealed interface Intent {
        data class RetentionChanged(val text: String) : Intent
        data class MaxEventsChanged(val text: String) : Intent

        /**
         * Saves the inputs: persists them with [DemoConfigRepository.save], applies them with
         * `EventTracker.updateConfig` and tracks `AppEvents.configChanged`, in this order. Ignored
         * when [State.canSave] is false. Unseen-events protection keeps its default values.
         */
        data object Save : Intent
    }

    sealed interface Effect {
        /** The config was saved and applied. */
        data object Saved : Effect

        /** Saving failed; the inputs are kept so that the user can retry. */
        data object SaveFailed : Effect
    }
}
