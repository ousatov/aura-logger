package com.usatov.aura.logger.demo.main

import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.DemoConfigRepository
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.main.MainContract.Effect
import com.usatov.aura.logger.demo.main.MainContract.Intent
import com.usatov.aura.logger.demo.main.MainContract.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App shell. Selecting a different tab updates [State.section] and tracks
 * [AppEvents.sectionSelected] unless [DemoConfigRepository.trackNavigation] is off; selecting the current tab again changes nothing and tracks nothing.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val appEvents: AppEvents,
    private val configRepository: DemoConfigRepository,
) : MviViewModel<State, Intent, Effect>(State()) {

    /** The stored flag; `null` until the first value is read. */
    @Volatile
    private var trackNavigation: Boolean? = null

    init {
        viewModelScope.launch {
            configRepository.trackNavigation.collect { trackNavigation = it }
        }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SelectSection -> selectSection(intent)
        }
    }

    private fun selectSection(intent: Intent.SelectSection) {
        if (intent.section == currentState.section) return
        setState { copy(section = intent.section) }
        val known = trackNavigation
        if (known != null) {
            if (known) appEvents.sectionSelected(intent.section)
        } else {
            // Not loaded yet: wait for the stored value instead of assuming "on".
            viewModelScope.launch {
                if (configRepository.trackNavigation.first()) appEvents.sectionSelected(intent.section)
            }
        }
    }
}
