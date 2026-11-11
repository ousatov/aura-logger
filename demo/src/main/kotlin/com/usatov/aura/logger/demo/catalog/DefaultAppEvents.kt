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

    override fun processStarted(): Unit = TODO("D1")

    override fun sectionSelected(section: Section): Unit = TODO("D1")

    override fun configChanged(config: EventTrackerConfig): Unit = TODO("D1")

    override fun eventsCleared(deletedCount: Int): Unit = TODO("D1")

    override fun testEvent(): Unit = TODO("D1")

    override fun testEvents(count: Int): Unit = TODO("D1")

    override fun testEventWithProperties(): Unit = TODO("D1")
}
