package com.usatov.aura.logger.demo.catalog

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.core.model.Section

/**
 * Catalog of every event the demo app tracks. The SDK knows nothing about these names; all event
 * names and properties of the app are defined here and nowhere else.
 *
 * Every function tracks synchronously through `EventTracker.track` (which never blocks or
 * throws) and never throws itself. Property maps keep the documented key order.
 */
interface AppEvents {

    /** `process_wake_up`, no properties. Tracked once per process start, before the SDK is initialised. */
    fun processStarted()

    /** `section_selected` with `{"section": section.key}`. */
    fun sectionSelected(section: Section)

    /**
     * `config_changed` with `{"retention_days": "<retentionDays>", "max_events": "<maxEvents>"}`
     * (decimal numbers as strings).
     */
    fun configChanged(config: EventTrackerConfig)

    /** `events_cleared` with `{"deleted": "<deletedCount>"}`. */
    fun eventsCleared(deletedCount: Int)

    /**
     * One test event named `test_event_` followed by exactly 6 random decimal digits, zero-padded
     * (for example `test_event_004213`), no properties.
     */
    fun testEvent()

    /** [count] test events as in [testEvent], each with its own random suffix; nothing if [count] <= 0. */
    fun testEvents(count: Int)

    /** `button_click` with `{"screen": "home", "action": "submit"}` in this order. */
    fun testEventWithProperties()
}

/** Event names and property keys of [AppEvents]. */
object AppEventNames {
    const val PROCESS_WAKE_UP: String = "process_wake_up"
    const val SECTION_SELECTED: String = "section_selected"
    const val CONFIG_CHANGED: String = "config_changed"
    const val EVENTS_CLEARED: String = "events_cleared"
    const val TEST_EVENT_PREFIX: String = "test_event_"
    const val BUTTON_CLICK: String = "button_click"

    const val KEY_SECTION: String = "section"
    const val KEY_RETENTION_DAYS: String = "retention_days"
    const val KEY_MAX_EVENTS: String = "max_events"
    const val KEY_DELETED: String = "deleted"
    const val KEY_SCREEN: String = "screen"
    const val KEY_ACTION: String = "action"
}
