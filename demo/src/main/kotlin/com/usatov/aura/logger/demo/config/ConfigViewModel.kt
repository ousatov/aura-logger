package com.usatov.aura.logger.demo.config

import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.config.ConfigContract.Effect
import com.usatov.aura.logger.demo.config.ConfigContract.Intent
import com.usatov.aura.logger.demo.config.ConfigContract.State
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

    override fun onIntent(intent: Intent): Unit = TODO("D5")
}
