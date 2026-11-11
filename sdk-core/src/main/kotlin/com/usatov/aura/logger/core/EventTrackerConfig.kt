package com.usatov.aura.logger.core

/**
 * Data retention policy of the event tracker.
 *
 * The policy is applied by the background cleanup (see [EventTracker.updateConfig] and the
 * SDK initialisation), never synchronously while tracking. Between two cleanup runs the number
 * of stored events can therefore exceed [maxEvents].
 *
 * Instances are immutable and safe to share between threads.
 *
 * @property retentionDays number of whole calendar days, before today, whose events are kept.
 * A cleanup deletes every event created before the start of the day that is [retentionDays]
 * days before the current day (device time zone at cleanup time). Example: with `7`, a cleanup
 * running on 15/03 keeps events created on 08/03 or later. Default: [DEFAULT_RETENTION_DAYS].
 * @property maxEvents maximum number of events to keep. A cleanup deletes the oldest events above
 * this number. Default: [DEFAULT_MAX_EVENTS].
 * @property unseenEventsProtection which cleanup operations must keep events that the user has
 * never seen. Because protected events are never deleted, [maxEvents] is a soft limit while
 * unseen events exist.
 * @throws IllegalArgumentException if [retentionDays] or [maxEvents] is not positive. This is a
 * programmer error and is reported when the config is created, not when it is used.
 */
public data class EventTrackerConfig(
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val maxEvents: Int = DEFAULT_MAX_EVENTS,
    val unseenEventsProtection: UnseenEventsProtection = UnseenEventsProtection(),
) {
    init {
        require(retentionDays > 0) { "retentionDays must be positive, was $retentionDays" }
        require(maxEvents > 0) { "maxEvents must be positive, was $maxEvents" }
    }

    public companion object {
        /** Default value of [EventTrackerConfig.retentionDays]. */
        public const val DEFAULT_RETENTION_DAYS: Int = 7

        /** Default value of [EventTrackerConfig.maxEvents]. */
        public const val DEFAULT_MAX_EVENTS: Int = 100
    }
}

/**
 * Protection of events that the user has never seen (see [EventTracker.markSeen]).
 *
 * Each flag independently controls one delete operation. When a flag is `true`, that operation
 * never deletes an unseen event; seen events are deleted as usual. When it is `false`, the
 * operation ignores the seen state.
 *
 * All flags are `true` by default: an event that the user has never seen is not eligible for
 * being cleared.
 *
 * @property onRetention protects unseen events from deletion by the retention period.
 * @property onLimit protects unseen events from deletion by the [EventTrackerConfig.maxEvents] limit.
 * Unseen events still count towards the limit, so older seen events are deleted first.
 * @property onClearAll protects unseen events from [EventTracker.clearAll].
 */
public data class UnseenEventsProtection(
    val onRetention: Boolean = true,
    val onLimit: Boolean = true,
    val onClearAll: Boolean = true,
)
