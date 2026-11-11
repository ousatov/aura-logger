package com.usatov.aura.logger.demo.events

import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Maps SDK events to rows; see [EventItem] for the formats. */
class EventFormatter @Inject constructor(
    private val zoneProvider: ZoneProvider,
) {
    fun format(event: TrackedEvent): EventItem = EventItem(
        id = event.id,
        timestamp = TIMESTAMP_FORMAT.withZone(zoneProvider.zone()).format(Instant.ofEpochMilli(event.timestamp)),
        name = event.name,
        properties = event.properties
            .takeIf { it.isNotEmpty() }
            ?.entries
            ?.joinToString(separator = ", ") { (key, value) -> "$key: $value" },
        seen = event.seen,
    )

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    }
}
