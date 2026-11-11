package com.usatov.aura.logger.core

/**
 * Data retention policy of the event tracker.
 *
 * The retention period is applied by the background cleanup (see [EventTracker.updateConfig] and
 * the SDK initialisation). The [maxEvents] limit is applied by the cleanup and also right after
 * every write on the background write queue, so the number of stored events exceeds [maxEvents]
 * only while unseen events protected by [UnseenEventsProtection.onLimit] prevent deletion.
 * With `onLimit` (the default), a newly tracked event is unseen and is never deleted by the limit.
 * With `onLimit = false`, the newest [maxEvents] events are kept, so a new event is deleted right
 * after it is written if at least [maxEvents] stored events are newer than it.
 *
 * Instances are immutable and safe to share between threads.
 *
 * @property retentionDays number of whole calendar days, before today, whose events are kept.
 * A cleanup deletes every event created before the start of the day that is [retentionDays]
 * days before the current day (device time zone at cleanup time). Example: with `7`, a cleanup
 * running on 15/03 keeps events created on 08/03 or later. Default: [DEFAULT_RETENTION_DAYS].
 * @property maxEvents maximum number of events to keep. When there are more, the oldest events
 * are deleted until at most [maxEvents] remain; with [UnseenEventsProtection.onLimit], only seen
 * events are deleted (oldest first), and if only unseen events are left above the limit, they are
 * all kept. Default: [DEFAULT_MAX_EVENTS].
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
 * Unseen events still count towards the limit. Seen events are deleted oldest first, wherever they
 * are in the list (also when they are newer than the unseen ones), until at most `maxEvents`
 * events remain or no seen event is left. Example: 10 events, seen are the 1st, 3rd, 5th, 7th and
 * 9th oldest, `maxEvents = 6`: the 1st, 3rd, 5th and 7th are deleted.
 * @property onClearAll protects unseen events from [EventTracker.clearAll].
 */
public data class UnseenEventsProtection(
    val onRetention: Boolean = true,
    val onLimit: Boolean = true,
    val onClearAll: Boolean = true,
)
