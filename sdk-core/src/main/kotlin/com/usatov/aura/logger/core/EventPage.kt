package com.usatov.aura.logger.core

/**
 * One page of events, newest first. See [EventTracker.getEventsPage].
 *
 * @property events events of this page, newest first. Never larger than the requested page
 * size. Can be empty.
 * @property nextCursor cursor of the next (older) page, or `null` when this page reached the
 * oldest stored event.
 */
public data class EventPage(
    val events: List<TrackedEvent>,
    val nextCursor: EventCursor?,
)

/**
 * Opaque position in the list of events ordered newest first.
 *
 * Obtain cursors only from [EventPage.nextCursor]. A cursor stays valid after events are added
 * or deleted: the next page starts with the newest event that is older than the last event of
 * the previous page, so no event is returned twice. Events tracked after the first page was
 * loaded are not part of later pages; reload from the first page to see them.
 *
 * Implements `equals`/`hashCode` so that it can be used as a paging key.
 */
public class EventCursor @InternalAuraLoggerApi constructor(
    /** Timestamp of the last event of the previous page. */
    @property:InternalAuraLoggerApi public val timestamp: Long,
    /** Insertion sequence of the last event of the previous page; breaks ties of equal timestamps. */
    @property:InternalAuraLoggerApi public val sequence: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is EventCursor && other.timestamp == timestamp && other.sequence == sequence

    override fun hashCode(): Int = 31 * timestamp.hashCode() + sequence.hashCode()

    override fun toString(): String = "EventCursor(timestamp=$timestamp, sequence=$sequence)"
}
