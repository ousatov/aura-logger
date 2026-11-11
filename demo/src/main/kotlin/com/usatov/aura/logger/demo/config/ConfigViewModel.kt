package com.usatov.aura.logger.demo.config

import androidx.lifecycle.viewModelScope
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val saveMutex = Mutex()

    /** The stored "track navigation" flag; `null` until loaded. */
    private var persistedTrackNavigation: Boolean? = null

    init {
        viewModelScope.launch {
            var first = true
            repository.trackNavigation.collect { enabled ->
                persistedTrackNavigation = enabled
                if (first) {
                    first = false
                    setState { copy(trackNavigation = enabled) }
                }
            }
        }
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
                            clearDeletesUnseen = !config.unseenEventsProtection.onClearAll,
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
            is Intent.TrackNavigationChanged -> setState { copy(trackNavigation = intent.enabled) }
            is Intent.ClearDeletesUnseenChanged -> setState { copy(clearDeletesUnseen = intent.enabled) }
            Intent.ScreenHidden -> persistSwitches()

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
            val config = EventTrackerConfig(
                retentionDays = retention,
                maxEvents = maxEvents,
                unseenEventsProtection = previous?.unseenEventsProtection ?: UnseenEventsProtection(),
            )
            try {
                repository.save(config)
                try {
                    tracker.updateConfig(config)
                } catch (e: CancellationException) {
                    // The SDK write is queued and still completes after the caller is cancelled,
                    // so the stored config (already the new one) must not be rolled back.
                    throw e
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

    private fun persistSwitches() {
        viewModelScope.launch {
            // Serialised and re-checked inside the lock: a tab switch followed by a lifecycle stop
            // must not save the same change twice.
            saveMutex.withLock {
                val state = currentState
                val saved = state.savedConfig ?: return@withLock
                try {
                    val storedNavigation = persistedTrackNavigation
                    if (storedNavigation != null && state.trackNavigation != storedNavigation) {
                        repository.saveTrackNavigation(state.trackNavigation)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    setState { copy(trackNavigation = persistedTrackNavigation ?: trackNavigation) }
                    sendEffect(Effect.SaveFailed)
                }
                if (state.clearDeletesUnseen == !saved.unseenEventsProtection.onClearAll) return@withLock
                val config = saved.copy(
                    unseenEventsProtection = saved.unseenEventsProtection.copy(onClearAll = !state.clearDeletesUnseen),
                )
                try {
                    repository.save(config)
                    try {
                        tracker.updateConfig(config)
                    } catch (e: CancellationException) {
                        throw e // see save(): the queued SDK write still completes
                    } catch (e: Exception) {
                        rollback(saved)
                        throw e
                    }
                    setState { copy(savedConfig = config) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    setState { copy(clearDeletesUnseen = !saved.unseenEventsProtection.onClearAll) }
                    sendEffect(Effect.SaveFailed)
                }
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
