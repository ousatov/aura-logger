package com.usatov.aura.logger.demo.actions

import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.actions.ActionsContract.Effect
import com.usatov.aura.logger.demo.actions.ActionsContract.Intent
import com.usatov.aura.logger.demo.actions.ActionsContract.State
import com.usatov.aura.logger.demo.catalog.AppEvents
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Test action buttons. See [ActionsContract]. */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val appEvents: AppEvents,
    private val tracker: EventTracker,
) : MviViewModel<State, Intent, Effect>(State()) {

    override fun onIntent(intent: Intent): Unit = TODO("D4")
}
