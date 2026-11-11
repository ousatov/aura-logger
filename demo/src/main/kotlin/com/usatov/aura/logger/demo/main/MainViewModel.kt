package com.usatov.aura.logger.demo.main

import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.DemoConfigRepository
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.main.MainContract.Effect
import com.usatov.aura.logger.demo.main.MainContract.Intent
import com.usatov.aura.logger.demo.main.MainContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App shell. Selecting a different tab updates [State.section] and tracks
 * [AppEvents.sectionSelected]; selecting the current tab again changes nothing and tracks nothing.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val appEvents: AppEvents,
    private val configRepository: DemoConfigRepository,
) : MviViewModel<State, Intent, Effect>(State()) {

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SelectSection -> selectSection(intent)
        }
    }

    private fun selectSection(intent: Intent.SelectSection) {
        if (intent.section == currentState.section) return
        setState { copy(section = intent.section) }
        appEvents.sectionSelected(intent.section)
    }
}
