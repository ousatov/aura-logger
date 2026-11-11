package com.usatov.aura.logger.demo.events

/**
 * MVI contract of the Events tab: all events, newest first, loaded page by page
 * (`EventsViewModel.events`), refreshed when events change.
 */
object EventsContract {

    /** Events per page requested from the SDK. */
    const val PAGE_SIZE: Int = 30

    /**
     * One row.
     *
     * @property timestamp `DD/MM/YYYY HH:MM:SS` (24-hour), device time zone at display time.
     * @property properties `key: value` pairs joined with `", "` in stored order, or `null` when the
     * event has no properties (then nothing is shown).
     */
    data class EventItem(
        val id: String,
        val timestamp: String,
        val name: String,
        val properties: String?,
        val seen: Boolean,
    )

    /** The list itself is `EventsViewModel.events`; the screen has no other state. */
    data object State

    sealed interface Intent {
        /**
         * Rows [ids] were displayed to the user. Their events are reported with
         * `EventTracker.markSeen`; ids already reported by this ViewModel or already seen are not
         * reported again. Empty lists are ignored.
         */
        data class ItemsShown(val ids: List<String>) : Intent
    }

    sealed interface Effect
}
