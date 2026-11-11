package com.usatov.aura.logger.demo.catalog

import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.core.model.Section
import javax.inject.Inject
import kotlin.random.Random

/**
 * [AppEvents] on top of the SDK.
 *
 * @param random source of the 6-digit suffix of test event names (`random.nextInt(0, 1_000_000)`).
 */
class DefaultAppEvents @Inject constructor(
    private val tracker: EventTracker,
    private val random: Random,
) : AppEvents {

    override fun processStarted() {
        tracker.track(AppEventNames.PROCESS_WAKE_UP)
    }

    override fun sectionSelected(section: Section) {
        tracker.track(
            AppEventNames.SECTION_SELECTED,
            linkedMapOf(AppEventNames.KEY_SECTION to section.key),
        )
    }

    override fun configChanged(config: EventTrackerConfig) {
        tracker.track(
            AppEventNames.CONFIG_CHANGED,
            linkedMapOf(
                AppEventNames.KEY_RETENTION_DAYS to config.retentionDays.toString(),
                AppEventNames.KEY_MAX_EVENTS to config.maxEvents.toString(),
            ),
        )
    }

    override fun eventsCleared(deletedCount: Int) {
        tracker.track(
            AppEventNames.EVENTS_CLEARED,
            linkedMapOf(AppEventNames.KEY_DELETED to deletedCount.toString()),
        )
    }

    override fun testEvent() {
        tracker.track(AppEventNames.TEST_EVENT_PREFIX + random.nextInt(0, SUFFIX_BOUND).toString().padStart(SUFFIX_DIGITS, '0'))
    }

    override fun testEvents(count: Int) {
        repeat(count) { testEvent() }
    }

    override fun testEventWithProperties() {
        tracker.track(
            AppEventNames.BUTTON_CLICK,
            linkedMapOf(
                AppEventNames.KEY_SCREEN to "home",
                AppEventNames.KEY_ACTION to "submit",
            ),
        )
    }

    private companion object {
        const val SUFFIX_DIGITS = 6
        const val SUFFIX_BOUND = 1_000_000
    }
}
