package com.usatov.aura.logger.demo.main

import com.usatov.aura.logger.demo.core.model.Section

/** MVI contract of the app shell (bottom navigation). */
object MainContract {

    /** @property section the selected tab; [Section.DASHBOARD] on start. */
    data class State(val section: Section = Section.DASHBOARD)

    sealed interface Intent {
        /** The user tapped a tab. */
        data class SelectSection(val section: Section) : Intent
    }

    /** The shell has no one-off effects. */
    sealed interface Effect
}
