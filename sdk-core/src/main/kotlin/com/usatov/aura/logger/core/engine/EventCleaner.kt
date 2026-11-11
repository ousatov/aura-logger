package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.InternalAuraLoggerApi
import com.usatov.aura.logger.core.internal.DayCalendar
import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.TimeZoneProvider
import kotlin.time.Clock

/**
 * Applies an [EventTrackerConfig] to stored events. Used by the background cleanup worker.
 *
 * One [cleanup] run, in this order:
 * 1. Retention: deletes events with a timestamp before the start of the day that is
 *    [EventTrackerConfig.retentionDays] days before the current day ([clock], time zone from
 *    [timeZoneProvider] at run time). Unseen events are kept if
 *    `unseenEventsProtection.onRetention` is set.
 * 2. Limit: deletes the oldest remaining events so that at most [EventTrackerConfig.maxEvents]
 *    remain. If `unseenEventsProtection.onLimit` is set, unseen events are kept but counted, and
 *    seen events are deleted oldest first until the limit is reached or no seen event is left.
 *
 * Storage errors are propagated to the caller (the worker decides whether to retry).
 */
@InternalAuraLoggerApi
public class EventCleaner(
    private val eventStore: EventStore,
    private val clock: Clock,
    private val timeZoneProvider: TimeZoneProvider,
    private val logger: AuraLogger,
) {
    /** Runs one cleanup with [config]. Main-safe. */
    public suspend fun cleanup(config: EventTrackerConfig): CleanupResult {
        val protection = config.unseenEventsProtection
        val now = clock.now().toEpochMilliseconds()
        val retentionStart = DayCalendar.startOfDay(
            epochMillis = now,
            zoneId = timeZoneProvider.currentTimeZoneId(),
            daysBefore = config.retentionDays.toLong(),
        )
        val deletedByRetention = eventStore.deleteOlderThan(retentionStart, keepUnseen = protection.onRetention)
        val deletedByLimit = eventStore.deleteOldestAbove(config.maxEvents, keepUnseen = protection.onLimit)
        if (protection.onLimit) {
            val remaining = eventStore.count()
            if (remaining > config.maxEvents) {
                logger.warn(
                    "$remaining events remain after cleanup (limit ${config.maxEvents}): " +
                        "unseen events are protected from the limit",
                )
            }
        }
        return CleanupResult(deletedByRetention = deletedByRetention, deletedByLimit = deletedByLimit)
    }
}

/**
 * Outcome of one [EventCleaner.cleanup] run.
 *
 * @property deletedByRetention number of events deleted by the retention period.
 * @property deletedByLimit number of events deleted by the max events limit.
 */
@InternalAuraLoggerApi
public data class CleanupResult(
    val deletedByRetention: Int,
    val deletedByLimit: Int,
)
