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
import kotlinx.coroutines.launch

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
        }
    }

    private fun save() {
        val snapshot = currentState
        if (!snapshot.canSave) return
        val retention = snapshot.retentionInput.trim().toIntOrNull() ?: return
        val maxEvents = snapshot.maxEventsInput.trim().toIntOrNull() ?: return
        setState { copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val config = EventTrackerConfig(retentionDays = retention, maxEvents = maxEvents)
                repository.save(config)
                tracker.updateConfig(config)
                appEvents.configChanged(config)
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
