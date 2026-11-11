package com.usatov.aura.logger.core

/**
 * Snapshot of event statistics.
 *
 * @property totalCount number of stored events.
 * @property todayCount number of stored events whose [TrackedEvent.timestamp] falls on the current
 * day, in the device time zone at the moment the statistics were computed.
 * @property dailyCounts number of stored events per [TrackedEvent.createdDate], one entry per day
 * that has at least one event, newest day first. Days are ordered by event timestamps, not by
 * comparing the `DD/MM/YYYY` strings. Empty when there are no events.
 */
public data class EventStats(
    val totalCount: Int,
    val todayCount: Int,
    val dailyCounts: List<DailyEventCount>,
)

/**
 * Number of events created on one day.
 *
 * @property date day in `DD/MM/YYYY` format, equal to [TrackedEvent.createdDate] of its events.
 * @property latestTimestamp the newest [TrackedEvent.timestamp] among the events of this day,
 * in Unix epoch milliseconds. Lets clients filter days by time (for example, "last 7 days")
 * without parsing [date].
 * @property count number of events on this day; always positive.
 */
public data class DailyEventCount(
    val date: String,
    val latestTimestamp: Long,
    val count: Int,
)
