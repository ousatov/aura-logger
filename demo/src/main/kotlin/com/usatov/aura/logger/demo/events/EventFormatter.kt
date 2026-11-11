package com.usatov.aura.logger.demo.events

import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import javax.inject.Inject

/** Maps SDK events to rows; see [EventItem] for the formats. */
class EventFormatter @Inject constructor(
    private val zoneProvider: ZoneProvider,
) {
    fun format(event: TrackedEvent): EventItem = TODO("D3")
}
