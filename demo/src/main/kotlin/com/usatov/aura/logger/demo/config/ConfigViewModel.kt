package com.usatov.aura.logger.demo.config

import androidx.lifecycle.viewModelScope
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.ConfigContract.Effect
import com.usatov.aura.logger.demo.config.ConfigContract.InputError
import com.usatov.aura.logger.demo.config.ConfigContract.Intent
import com.usatov.aura.logger.demo.config.ConfigContract.State
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Config section. On creation it loads [DemoConfigRepository.config] and fills both inputs and
 * [State.savedConfig]; later emissions update [State.savedConfig] only (inputs being edited are
 * not overwritten). See [ConfigContract] for intents and validation.
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: DemoConfigRepository,
    private val tracker: EventTracker,
    private val appEvents: AppEvents,
) : MviViewModel<State, Intent, Effect>(State()) {

    init {
        viewModelScope.launch {
            var first = true
            repository.config.collect { config ->
                if (first) {
                    first = false
                    setState {
                        copy(
                            retentionInput = config.retentionDays.toString(),
                            maxEventsInput = config.maxEvents.toString(),
                            retentionError = null,
                            maxEventsError = null,
                            savedConfig = config,
                        )
                    }
                } else {
                    setState { copy(savedConfig = config) }
                }
            }
        }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.RetentionChanged -> setState {
                copy(
                    retentionInput = intent.text,
                    retentionError = validate(intent.text, ConfigContract.RETENTION_DAYS_RANGE),
                )
            }
            is Intent.MaxEventsChanged -> setState {
                copy(
                    maxEventsInput = intent.text,
                    maxEventsError = validate(intent.text, ConfigContract.MAX_EVENTS_RANGE),
                )
            }
            Intent.Save -> save()
            is Intent.TrackNavigationChanged, is Intent.ClearDeletesUnseenChanged, Intent.ScreenHidden -> Unit

        }
    }

    private fun save() {
        val snapshot = currentState
        if (!snapshot.canSave) return
        val retention = snapshot.retentionInput.trim().toIntOrNull() ?: return
        val maxEvents = snapshot.maxEventsInput.trim().toIntOrNull() ?: return
        setState { copy(isSaving = true) }
        val previous = snapshot.savedConfig
        viewModelScope.launch {
            val config = EventTrackerConfig(retentionDays = retention, maxEvents = maxEvents)
            try {
                repository.save(config)
                try {
                    tracker.updateConfig(config)
                } catch (e: Exception) {
                    // Keep the stored config and the SDK config consistent: undo the write so
                    // that the inputs differ from the saved config again and Save can be retried.
                    if (previous != null) rollback(previous)
                    throw e
                }
                try {
                    appEvents.configChanged(config)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The config is stored and applied; a failed analytics event is not a save failure.
                }
                setState { copy(isSaving = false, savedConfig = config) }
                sendEffect(Effect.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                setState { copy(isSaving = false) }
                sendEffect(Effect.SaveFailed)
            }
        }
    }

    private suspend fun rollback(previous: EventTrackerConfig) {
        try {
            withContext(NonCancellable) { repository.save(previous) }
        } catch (_: Exception) {
            // Best effort: the failure is reported to the user either way.
        }
        setState { copy(savedConfig = previous) }
    }

    private companion object {
        private val INTEGER = Regex("[+-]?\\d+")

        fun validate(text: String, range: IntRange): InputError? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return InputError.EMPTY
            val value = trimmed.toIntOrNull()
                ?: return if (INTEGER.matches(trimmed)) InputError.OUT_OF_RANGE else InputError.NOT_A_NUMBER
            return if (value in range) null else InputError.OUT_OF_RANGE
        }
    }
}
