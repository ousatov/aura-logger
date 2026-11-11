package com.usatov.aura.logger.demo.events

/**
 * MVI contract of the Events tab: all events, newest first, grouped by day, loaded page by page
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

    /**
     * One entry of the list: a [DayHeader] before the first (newest) event of every day, then the
     * [Event]s of that day.
     */
    sealed interface EventListItem {

        /**
         * Header of one day.
         *
         * @property dayKey the day as `DD/MM/YYYY` in the device time zone at display time.
         * @property label what the header shows.
         */
        data class DayHeader(val dayKey: String, val label: DayLabel) : EventListItem

        /** One event row. */
        data class Event(val item: EventItem) : EventListItem
    }

    /**
     * Label of a [EventListItem.DayHeader]. [Today] and [Yesterday] are relative to the app clock
     * (debug time shift included) in the device time zone when the list is (re)loaded; labels follow
     * the next reload after `EventTracker.changes`, there is no timer.
     */
    sealed interface DayLabel {
        data object Today : DayLabel
        data object Yesterday : DayLabel

        /** Any older (or future) day; [text] is `DD/MM/YYYY`. */
        data class Date(val text: String) : DayLabel
    }

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
